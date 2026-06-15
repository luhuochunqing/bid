package com.xiyu.bid.systems.external;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class ExternalMenuTreeNode {
    private final String id;
    private final String menuName;
    private final String parentId;
    private final String menuCode;
    private final List<ExternalMenuTreeNode> children;
}
