package com.studyforyou_retry.modules.study;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEvent;
import org.springframework.stereotype.Component;

@Getter
@RequiredArgsConstructor
public class StudyCreatedEvent {
    private final Study study;
}
