package com.xiyu.bid.biddraftagent.domain;

import java.util.List;

public record GapCheckResult(boolean ready, List<String> gaps, List<String> suggestions) {

  public GapCheckResult {
    gaps = List.copyOf(gaps);
    suggestions = List.copyOf(suggestions);
  }
}
