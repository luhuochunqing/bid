package com.xiyu.bid.projectworkflow.service;

import com.xiyu.bid.projectworkflow.core.TaskBreakdownPolicy;

import java.util.List;

public interface ProjectTaskRequirementSourceGateway {

    List<TaskBreakdownPolicy.SourceSnapshot> latestRequirementSourcesForProject(Long projectId);
}
