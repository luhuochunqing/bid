package com.xiyu.bid.testing;

final class PredefinedStateMachines {

    private PredefinedStateMachines() {}

    static StateMachineValidator collaborationThread() {
        return StateMachineValidator.builder()
                .entity("collaboration_thread")
                .states("OPEN", "IN_PROGRESS", "RESOLVED", "CLOSED")
                .initialState("OPEN")
                .transition("OPEN", "IN_PROGRESS")
                .transition("OPEN", "CLOSED")
                .transition("IN_PROGRESS", "RESOLVED")
                .transition("IN_PROGRESS", "CLOSED")
                .transition("RESOLVED", "CLOSED")
                .build();
    }

    static StateMachineValidator project() {
        return StateMachineValidator.builder()
                .entity("project")
                .states("DRAFT", "IN_PROGRESS", "REVIEW", "APPROVED", "REJECTED", "COMPLETED", "CANCELLED")
                .initialState("DRAFT")
                .transition("DRAFT", "IN_PROGRESS")
                .transition("IN_PROGRESS", "REVIEW")
                .transition("REVIEW", "APPROVED")
                .transition("REVIEW", "REJECTED")
                .transition("APPROVED", "COMPLETED")
                .transition("DRAFT", "CANCELLED")
                .transition("IN_PROGRESS", "CANCELLED")
                .transition("REVIEW", "CANCELLED")
                .build();
    }

    static StateMachineValidator task() {
        return StateMachineValidator.builder()
                .entity("task")
                .states("TODO", "IN_PROGRESS", "REVIEW", "DONE", "CANCELLED")
                .initialState("TODO")
                .transition("TODO", "IN_PROGRESS")
                .transition("TODO", "CANCELLED")
                .transition("IN_PROGRESS", "REVIEW")
                .transition("IN_PROGRESS", "CANCELLED")
                .transition("REVIEW", "DONE")
                .transition("REVIEW", "IN_PROGRESS")
                .build();
    }

    static StateMachineValidator fee() {
        return StateMachineValidator.builder()
                .entity("fee")
                .states("PENDING", "PAID", "RETURNED", "CANCELLED")
                .initialState("PENDING")
                .transition("PENDING", "PAID")
                .transition("PAID", "RETURNED")
                .transition("PENDING", "CANCELLED")
                .transition("RETURNED", "PAID")
                .build();
    }

    static StateMachineValidator documentVersion() {
        return StateMachineValidator.builder()
                .entity("document_version")
                .states("DRAFT", "PUBLISHED", "ARCHIVED")
                .initialState("DRAFT")
                .transition("DRAFT", "PUBLISHED")
                .transition("PUBLISHED", "ARCHIVED")
                .transition("ARCHIVED", "DRAFT")
                .build();
    }
}
