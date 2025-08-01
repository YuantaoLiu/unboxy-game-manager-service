package com.unboxy.gamemanagerservice.utils;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MetadataUtils {
    public  <S, T> T copy(S source, T target) {
        BeanUtils.copyProperties(source, target);
        return target;
    }
}
