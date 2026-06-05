package com.xiyu.bid.contractborrow.application.command;

public record ContractBorrowActionCommand(
    String actorName,
    String comment,
    String reason
) {
    public String effectiveComment() {
        if (comment != null && !comment.isBlank()) {
            return comment;
        }
        return reason;
    }
}
