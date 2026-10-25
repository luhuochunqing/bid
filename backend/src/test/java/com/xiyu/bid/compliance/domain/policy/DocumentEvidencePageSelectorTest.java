package com.xiyu.bid.compliance.domain.policy;
import com.xiyu.bid.compliance.domain.DocumentContent;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
class DocumentEvidencePageSelectorTest {
 @Test void retainsLateCommitmentsAndScanNeighborsWithoutRenderingUnrelatedPages() {
  var doc=new DocumentContent("bid.pdf","hash","正文",List.of(
    new DocumentContent.PageContent(5,"目录 承诺函......1048",List.of()),
    new DocumentContent.PageContent(10,"法定代表人身份证明",List.of()),
    new DocumentContent.PageContent(11,"11",List.of()),
    new DocumentContent.PageContent(500,"普通技术正文",List.of()),
    new DocumentContent.PageContent(1048,"承诺函\n投标人：（盖章）",List.of())));
  assertThat(DocumentEvidencePageSelector.select(doc)).contains(10,11,1048).doesNotContain(5,500);
 }

 @Test void renderingIsAlwaysASupersetOfSealTargets() {
  var doc = DocumentContent.fromText("bid.pdf", "hash", "投标人：甲公司（盖单位章）");
  assertThat(DocumentEvidencePageSelector.select(doc)).containsAll(DocumentEvidencePageSelector.sealTargets(doc)).contains(1);
 }
}
