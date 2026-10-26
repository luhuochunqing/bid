package com.xiyu.bid.scoreparse.application.match;

import com.xiyu.bid.businessqualification.infrastructure.persistence.entity.BusinessQualificationEntity;
import com.xiyu.bid.businessqualification.infrastructure.persistence.repository.BusinessQualificationJpaRepository;
import com.xiyu.bid.businessqualification.domain.valueobject.*;
import com.xiyu.bid.scoreparse.dto.CertMatchRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.*;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes=CertificateScoreMysqlTest.Config.class,properties={"spring.jpa.hibernate.ddl-auto=create-drop","spring.flyway.enabled=false","spring.jpa.open-in-view=false"})
class CertificateScoreMysqlTest {
 static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.0");
 @DynamicPropertySource static void db(DynamicPropertyRegistry r){
  String url=System.getProperty("certificate.test.mysql.url");
  if(url==null){MYSQL.start();r.add("spring.datasource.url",MYSQL::getJdbcUrl);r.add("spring.datasource.username",MYSQL::getUsername);r.add("spring.datasource.password",MYSQL::getPassword);}
  else {
   if(!url.matches("jdbc:mysql://(?:localhost|127\\.0\\.0\\.1):[0-9]+/cert_score_test_[a-zA-Z0-9_]+(?:\\?.*)?")) throw new IllegalArgumentException("Only isolated local cert_score_test_* schemas are allowed");
   r.add("spring.datasource.url",()->url);r.add("spring.datasource.username",()->"root");r.add("spring.datasource.password",()->"");}
 }
 @AfterAll static void stop(){if(MYSQL.isRunning())MYSQL.stop();}
 @Configuration @EnableAutoConfiguration @EntityScan(basePackageClasses=BusinessQualificationEntity.class)
 @EnableJpaRepositories(basePackageClasses=BusinessQualificationJpaRepository.class) @Import(CertMatchService.class)
 static class Config {}
 @Autowired BusinessQualificationJpaRepository repository;
 @Autowired CertMatchService service;
 static final LocalDate DAY=LocalDate.of(2026,10,26);
 static final String ISO="具有ISO9001质量管理体系认证、ISO14001环境管理体系认证、ISO45001职业健康安全管理体系认证的得10分。三项齐全得满分，缺少一项，不得分";
 @BeforeEach void clean(){repository.deleteAll();}
 void save(String name){repository.saveAndFlush(BusinessQualificationEntity.builder().name(name).subjectName("fixture").subjectType(QualificationSubjectType.values()[0]).category(QualificationCategory.values()[0]).status(QualificationStatus.IN_STOCK).expiryDate(DAY.plusYears(1)).build());}
 @Test void eachMissingStandardMustFail(){
  for(String missing:List.of("ISO9001","ISO14001","ISO45001")) {
   repository.deleteAll();for(String id:List.of("ISO9001","ISO14001","ISO45001")) if(!id.equals(missing))save(id);
   var result=service.estimateScore("管理体系",ISO,BigDecimal.TEN,DAY).assessment();
   assertThat(result.score()).as("missing %s",missing).isZero();
   assertThat(result.status()).isEqualTo("DANGER");
  }
 }
 @Test void realSnapshotAcceptsAliasesAndFingerprintsFactsAndDate(){
  save("GB/T19001认证");save("ISO 14001环境体系");save("ISO45001职业健康管理认证证书");
  var first=service.estimateScore("管理体系",ISO,BigDecimal.TEN,DAY);
  assertThat(first.assessment().score()).isEqualByComparingTo("10");
  assertThat(first.inputFingerprint()).isEqualTo(service.fingerprintForScore("管理体系",ISO,BigDecimal.TEN,DAY));
  assertThat(service.fingerprintForScore("管理体系",ISO,BigDecimal.TEN,DAY.plusDays(1))).isNotEqualTo(first.inputFingerprint());
  var cert=repository.findAll().get(0);cert.setRetired(true);repository.saveAndFlush(cert);
  var changed=service.estimateScore("管理体系",ISO,BigDecimal.TEN,DAY);
  assertThat(changed.assessment().score()).isZero();
  assertThat(changed.inputFingerprint()).isNotEqualTo(first.inputFingerprint());
 }
 @Test void unrelatedAndUnknownValidityCannotEarnFullScore(){
  save("ISO20000");save("ISO27001");
  assertThat(service.estimateScore("管理体系",ISO,BigDecimal.TEN,DAY).assessment().score()).isZero();
  repository.deleteAll();save("ISO9001");save("ISO14001");save("ISO45001");
  var cert=repository.findAll().get(0);cert.setExpiryDate(null);repository.saveAndFlush(cert);
  assertThat(service.estimateScore("管理体系",ISO,BigDecimal.TEN,DAY).assessment().status()).isEqualTo("PENDING");
 }
 @Test void honorStaircaseIsEvaluatedFromRealDistinctFacts(){
  String rule="具有中国品牌信誉评价认证五星级证书；企业质量信用等级评价认证3A级证书；商品售后五星级认证；企业标准化管理体系认证3A级证书；以上4项，具备任意4项则得5分，具备任意3项得3分，具备任意2项得2分，具备任意1项的得1分，不满足任意1项或未提供证明文件者，不得分";
  var names=List.of("中国品牌信誉评价认证五星级证书","企业质量信用等级评价认证3A级证书","商品售后五星级认证","企业标准化管理体系认证3A级证书");
  String[] expected={"0","1","2","3","5"};
  for(int n=0;n<=4;n++) {
   if(n>0)save(names.get(n-1));
   var result=service.estimateScore("企业荣誉",rule,new BigDecimal("5"),DAY).assessment();
   assertThat(result.score()).isEqualByComparingTo(expected[n]);
   assertThat(result.status()).isEqualTo(n==0?"DANGER":n==4?"OK":"PENDING");
  }
 }

 @Test void personnelCategoryCannotProveCorporateCertificationAndChangesFingerprint(){
  save("ISO9001");
  var first=service.estimateScore("资质","具有ISO9001质量管理体系认证证书",BigDecimal.TEN,DAY);
  assertThat(first.assessment().score()).isEqualByComparingTo("10");
  var row=repository.findAll().get(0);row.setCategory(QualificationCategory.PERSONNEL);repository.saveAndFlush(row);
  var changed=service.estimateScore("资质","具有ISO9001质量管理体系认证证书",BigDecimal.TEN,DAY);
  assertThat(changed.assessment().score()).isZero();
  assertThat(changed.inputFingerprint()).isNotEqualTo(first.inputFingerprint());
  row.setCategory(QualificationCategory.OTHER);row.setName("ISO9001质量管理体系内审员培训合格证书");repository.saveAndFlush(row);
  assertThat(service.estimateScore("资质","具有ISO9001质量管理体系认证证书",BigDecimal.TEN,DAY).assessment().score()).isZero();
 }

}
