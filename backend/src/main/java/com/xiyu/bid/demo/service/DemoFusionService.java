package com.xiyu.bid.demo.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Service
public class DemoFusionService {

    public <T, K> List<T> mergeByKey(List<T> realData, List<T> demoData, Function<T, K> keyExtractor) {
        Map<K, T> merged = new LinkedHashMap<>();
        for (T item : realData) {
            merged.put(keyExtractor.apply(item), item);
        }
        for (T item : demoData) {
            merged.putIfAbsent(keyExtractor.apply(item), item);
        }
        return new ArrayList<>(merged.values());
    }

    public <T> List<T> mergeAndSort(List<T> realData, List<T> demoData, Comparator<T> comparator) {
        List<T> merged = new ArrayList<>(realData.size() + demoData.size());
        merged.addAll(realData);
        merged.addAll(demoData);
        merged.sort(comparator);
        return merged;
    }

    public <T, K> Page<T> mergePage(Page<T> realPage, List<T> demoData, Function<T, K> keyExtractor) {
        Pageable pageable = realPage.getPageable();
        List<T> merged = mergeByKey(realPage.getContent(), demoData, keyExtractor);
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), merged.size());
        List<T> content;
        if (start >= merged.size()) {
            content = List.of();
        } else {
            content = merged.subList(start, end);
        }
        return new PageImpl<>(content, pageable, merged.size());
    }
}
