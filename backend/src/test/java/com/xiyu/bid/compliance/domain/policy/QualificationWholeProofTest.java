package com.xiyu.bid.compliance.domain.policy;
import com.xiyu.bid.compliance.domain.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
class QualificationWholeProofTest {
    @Test void unrelatedCertificateCannotProveWholeQualification() {
        String body="营业执照：证书编号ABC12345，有效期限2020年1月1日至2030年1月1日。业绩合同：合同签订日期2020年5月27日。";
        var document=new DocumentContent("synthetic.docx","hash",body,List.of(new DocumentContent.PageContent(1,body,List.of())));
        var pass=QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_VETO,"全部资格满足",List.of());
        assertThat(SemanticEvidenceBoundary.requireQualificationConfirmation(pass,document).status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
    }
}
