package com.xiyu.bid.businessqualification.application.command;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class QualificationReturnCommand {
    String returnRemark;
}
