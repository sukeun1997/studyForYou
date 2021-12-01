package com.studyforyou.event;

import com.studyforyou.WithAccount;
import com.studyforyou.constant.EventType;
import com.studyforyou.domain.Account;
import com.studyforyou.domain.Enrollment;
import com.studyforyou.domain.Event;
import com.studyforyou.repository.EnrollmentRepository;
import com.studyforyou.repository.EventRepository;
import com.studyforyou.study.StudyController;
import com.studyforyou.study.StudyControllerTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:application-test.properties")
class EventControllerTest extends StudyControllerTest {


    @Autowired
    EventService eventService;

    @Autowired
    EventRepository eventRepository;

    @Autowired
    EnrollmentRepository enrollmentRepository;

    @Test
    @WithAccount("test")
    @DisplayName("모임 생성 폼")
    void createEventView() throws Exception {

        mockMvc.perform(get("/study/"+study.getPath()+"/new-event"))
                .andExpect(model().attributeExists("account"))
                .andExpect(model().attributeExists("study"))
                .andExpect(model().attributeExists("eventForm"))
                .andExpect(status().isOk())
                .andExpect(view().name("/event/form"));
    }

    Account createAccount() {
        Account account = new Account();
        return accountRepository.save(account);
    }

    public Event createEvent() {

        Event event = Event.builder().eventType(EventType.FCFS)
                .description("gd")
                .createdBy(account)
                .title("gd")
                .study(study)
                .limitOfEnrollments(3)
                .enrollments(new ArrayList<>())
                .createdDateTime(LocalDateTime.now())
                .startDateTime(LocalDateTime.now())
                .endDateTime(LocalDateTime.now().plusDays(1))
                .endEnrollmentDateTime(LocalDateTime.now().plusHours(3)).build();
        return eventRepository.save(event);
    }

    @Test
    @WithAccount("test")
    @DisplayName("선착순 모임에 참가 신청 -자동 수락")
    void enroll_true() throws Exception {

        Event event = createEvent();

        mockMvc.perform(post("/study/" + study.getPath() + "/events/" + event.getId() + "/enroll")
                .param("path", study.getPath())
                .param("eventId", String.valueOf(event.getId()))
                .with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertTrue(enrollmentRepository.existsByEventAndAccount(event,account));

        Enrollment enrollment = enrollmentRepository.findByEventAndAccount(event,account);
        assertTrue(enrollment.isAccepted());

    }


    @Test
    @WithAccount("test")
    @DisplayName("선착순 모임에 참가 신청 - 대기중 ( 인원 꽉참 )")
    void enroll_fail() throws Exception {

        Event event = createEvent();

        createEnroll(event, 3);

        assertEquals(event.getEnrollments().size(), 3);
        mockMvc.perform(post("/study/" + study.getPath() + "/events/" + event.getId() + "/enroll")
                        .param("path", study.getPath())
                        .param("eventId", String.valueOf(event.getId()))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertTrue(enrollmentRepository.existsByEventAndAccount(event,account));

        Enrollment enrollment = enrollmentRepository.findByEventAndAccount(event,account);
        assertFalse(enrollment.isAccepted());

    }

    @Test
    @WithAccount("test")
    @DisplayName("선착순 모임에 참가 취소 후 대기자 자동 확정")
    void disenroll_success() throws Exception {

        Event event = createEvent();

        eventService.enrollEvent(account,event);
        assertTrue(enrollmentRepository.existsByEventAndAccount(event,account));
        Enrollment enrollment = enrollmentRepository.findByEventAndAccount(event,account);
        assertTrue(enrollment.isAccepted());

        createEnroll(event, 3);

        assertEquals(event.getEnrollments().size(), 4);
        assertTrue(event.getWaitingList().size() == 1);

        mockMvc.perform(post("/study/" + study.getPath() + "/events/" + event.getId() + "/disenroll")
                        .param("path", study.getPath())
                        .param("eventId", String.valueOf(event.getId()))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertTrue(!enrollmentRepository.existsByEventAndAccount(event,account));
        assertTrue(event.getWaitingList().size() == 0);
    }

    @Test
    @WithAccount("test")
    @DisplayName("선착순 모임에 확정 아닌 참가자가 취소 후 대기자 자동 확정 변동X")
    void disenroll_no_change() throws Exception {

        Event event = createEvent();
        createEnroll(event,4);

        eventService.enrollEvent(account,event);
        assertTrue(enrollmentRepository.existsByEventAndAccount(event,account));
        Enrollment enrollment = enrollmentRepository.findByEventAndAccount(event,account);
        assertTrue(!enrollment.isAccepted());

        assertEquals(event.getEnrollments().size(), 5);
        assertTrue(event.getWaitingList().size() == 2);

        mockMvc.perform(post("/study/" + study.getPath() + "/events/" + event.getId() + "/disenroll")
                        .param("path", study.getPath())
                        .param("eventId", String.valueOf(event.getId()))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertTrue(!enrollmentRepository.existsByEventAndAccount(event,account));
        assertTrue(event.getWaitingList().size() == 1);
    }
    @Test
    @WithAccount("test")
    @DisplayName("선착순 모임 인원을 늘릴시 대기중 자동 확정 ( 대기중 인원이 모집 인원보다 더 많을 때 )")
    void accept_waiting_1() throws Exception {

        Event event = createEvent();
        createEnroll(event,7); // 대기중 4

        event.setLimitOfEnrollments(5);
        event.acceptWaitingEnrollment();

        assertEquals(event.getWaitingList().size(), 2);
    }

    @Test
    @WithAccount("test")
    @DisplayName("선착순 모임 인원을 늘릴시 대기중 자동 확정 ( 늘린 모집 인원이 대기중 인원보다 더 많을 때 )")
    void accept_waiting_2() throws Exception {

        Event event = createEvent();
        createEnroll(event,7); // 대기중 4

        event.setLimitOfEnrollments(10);
        event.acceptWaitingEnrollment();

        assertEquals(event.getWaitingList().size(), 0);
    }

        private void createEnroll(Event event,int count) {
        for (int i = 0; i <count; i++) {
            Account account1 = createAccount();
            eventService.enrollEvent(account1, event);
        }
    }


}