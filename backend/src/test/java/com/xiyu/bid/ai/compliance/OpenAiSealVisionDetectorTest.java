package com.xiyu.bid.ai.compliance;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.PageImage;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
class OpenAiSealVisionDetectorTest {
 @Test void promptMapsImagesToPhysicalPages() throws Exception {
  var images=List.of(new PageImage(1,"image/png","page",1048,100,100,"aGk="));
  var method=OpenAiSealVisionDetector.class.getDeclaredMethod("buildPrompt",DocumentContent.class,List.class);
  method.setAccessible(true);
  String prompt=(String)method.invoke(null,new DocumentContent("bid.pdf","hash","正文",List.of()),images);
  assertThat(prompt).contains("1048").contains("第1张图片");
 }

 @Test void modelCannotShrinkTargetsOrInventAnUnsentPage() {
  var service=org.mockito.Mockito.mock(com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiStructuredOutputService.class);
  var resolver=org.mockito.Mockito.mock(com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiBidAgentConfigurationResolver.class);
  var config=new com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiBidAgentRequestConfig("test","http://localhost","model",java.time.Duration.ofSeconds(1),null);
  org.mockito.Mockito.when(resolver.resolve(org.mockito.ArgumentMatchers.anyString())).thenReturn(config);
  var out=new OpenAiSealVisionDetector.Output();out.targetPages=List.of(11);
  var observation=new OpenAiSealVisionDetector.Observation();observation.pageNumber=1048;observation.type="COMPANY_SEAL";observation.detected=true;observation.confidence=0.99;out.observations=List.of(observation);
  org.mockito.Mockito.when(service.request(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyList(),org.mockito.ArgumentMatchers.eq(OpenAiSealVisionDetector.Output.class),org.mockito.ArgumentMatchers.eq(config),org.mockito.ArgumentMatchers.anyString())).thenReturn(out);
  var doc=new DocumentContent("bid.pdf","hash","正文",List.of(new DocumentContent.PageContent(10,"投标人：（盖章）",List.of()),new DocumentContent.PageContent(11,"投标人：（盖章）",List.of())),List.of(new PageImage(1,"image/png","page",10,100,100,"aGk="),new PageImage(2,"image/png","page",11,100,100,"aGk=")));
  var result=new OpenAiSealVisionDetector(service,resolver).detect(doc);
  assertThat(result.targetPages()).containsExactly(10,11);
  assertThat(result.observations()).isEmpty();
 }

 @Test void ordinaryTechnicalTextIsNotASealTarget() throws Exception {
  var doc=new DocumentContent("bid.pdf","hash","正文",List.of(new DocumentContent.PageContent(1,"投标函 投标人：（盖章）",List.of()),new DocumentContent.PageContent(2,"普通技术方案正文与工艺介绍。".repeat(20),List.of())),List.of());
  var method=OpenAiSealVisionDetector.class.getDeclaredMethod("targetPages",DocumentContent.class,List.class);method.setAccessible(true);
  assertThat((List<?>)method.invoke(null,doc,List.of(new PageImage(1,"image/png","page",1,100,100,"aGk=")))).isEqualTo(List.of(1));
 }
 @Test void nearbyScannedAuthorizationPageRemainsASealTargetWhenOcrFails() throws Exception {
  var doc=new DocumentContent("bid.pdf","hash","正文",List.of(new DocumentContent.PageContent(10,"授权委托书 投标人：（盖章）",List.of()),new DocumentContent.PageContent(11,"11",List.of())),List.of());
  var method=OpenAiSealVisionDetector.class.getDeclaredMethod("targetPages",DocumentContent.class,List.class);method.setAccessible(true);
  assertThat((List<?>)method.invoke(null,doc,List.of(new PageImage(1,"image/png","page",10,100,100,"aGk="),new PageImage(2,"image/png","page",11,100,100,"aGk=")))).isEqualTo(List.of(10,11));
 }
}
