package com.xiyu.bid.systems.external;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class ExternalMenuResponse {
    private final String systemCode;
    private final String systemName;
    private final List<ExternalMenuTreeNode> menus;
}
