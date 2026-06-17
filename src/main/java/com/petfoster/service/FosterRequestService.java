package com.petfoster.service;

import com.petfoster.annotation.RequireOwner;
import com.petfoster.annotation.ResourceType;
import com.petfoster.common.BusinessException;
import com.petfoster.common.PageResponse;
import com.petfoster.dto.FosterRequestDTO;
import com.petfoster.entity.FosterRequest;
import com.petfoster.entity.Notification;
import com.petfoster.entity.Pet;
import com.petfoster.entity.User;
import com.petfoster.event.NotificationEvent;
import com.petfoster.repository.FosterRequestRepository;
import com.petfoster.repository.PetRepository;
import com.petfoster.repository.UserRepository;
import com.petfoster.util.EntityMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class FosterRequestService {

    private final FosterRequestRepository requestRepository;
    private final PetRepository petRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    private static final Set<FosterRequest.Status> ALLOWED_FROM_PENDING = Set.of(
            FosterRequest.Status.Approved, FosterRequest.Status.Cancelled);
    private static final Set<FosterRequest.Status> ALLOWED_FROM_APPROVED = Set.of(
            FosterRequest.Status.InProgress, FosterRequest.Status.Cancelled);
    private static final Set<FosterRequest.Status> ALLOWED_FROM_IN_PROGRESS = Set.of(
            FosterRequest.Status.Completed, FosterRequest.Status.Cancelled);

    public PageResponse<FosterRequestDTO.RequestResponse> getRequests(
            int page, int size, String sort,
            FosterRequest.Status status, Long ownerId, Long fostererId, Long petId) {

        Sort sortObj = parseSort(sort);
        Pageable pageable = PageRequest.of(page, size, sortObj);

        Page<FosterRequest> requestPage = requestRepository.searchRequests(
                status, ownerId, fostererId, petId, pageable);

        return buildPageResponse(requestPage);
    }

    public FosterRequestDTO.RequestResponse getRequestById(Long id) {
        FosterRequest request = requestRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("寄养申请不存在"));
        return buildSingleResponse(request);
    }

    public PageResponse<FosterRequestDTO.RequestResponse> getMyRequests(
            Long userId, int page, int size, String sort) {

        Sort sortObj = parseSort(sort);
        Pageable pageable = PageRequest.of(page, size, sortObj);

        Page<FosterRequest> requestPage = requestRepository.findByUserId(userId, pageable);
        return buildPageResponse(requestPage);
    }

    @Transactional
    @RequireOwner(resource = ResourceType.PET, idParam = "#req.petId", message = "只能为自己的宠物创建寄养申请")
    public FosterRequestDTO.RequestResponse createRequest(Long userId, FosterRequestDTO.CreateRequest req) {
        Pet pet = petRepository.findById(req.getPetId())
                .orElseThrow(() -> BusinessException.notFound("宠物不存在"));

        if (req.getStartDate().isAfter(req.getEndDate())) {
            throw BusinessException.badRequest("开始日期不能晚于结束日期");
        }
        if (req.getEndDate().isBefore(LocalDate.now())) {
            throw BusinessException.badRequest("结束日期不能早于今天");
        }

        if (req.getFostererId() != null && req.getFostererId().equals(userId)) {
            throw BusinessException.badRequest("不能寄养自己的宠物");
        }

        FosterRequest.Status initialStatus = req.getFostererId() != null
                ? FosterRequest.Status.Pending : FosterRequest.Status.Pending;

        FosterRequest request = FosterRequest.builder()
                .petId(req.getPetId())
                .ownerId(userId)
                .fostererId(req.getFostererId())
                .startDate(req.getStartDate())
                .endDate(req.getEndDate())
                .dailyCareNotes(req.getDailyCareNotes())
                .status(initialStatus)
                .build();

        request = requestRepository.save(request);
        log.info("寄养申请创建成功: requestId={}, ownerId={}", request.getId(), userId);

        if (req.getFostererId() != null) {
            String petName = pet.getName();
            User owner = userRepository.findById(userId).orElse(null);
            String ownerName = owner != null ? owner.getUsername() : "某位主人";
            eventPublisher.publishEvent(new NotificationEvent(List.of(
                    NotificationEvent.entry(
                            req.getFostererId(),
                            Notification.Type.FOSTER_REQUEST_CREATED,
                            "收到新的寄养申请",
                            String.format("%s 邀请您帮忙寄养宠物「%s」，寄养时间：%s 至 %s",
                                    ownerName, petName, req.getStartDate(), req.getEndDate()),
                            request.getId(),
                            Notification.RelatedType.FOSTER_REQUEST
                    )
            )));
        }

        return buildSingleResponse(request);
    }

    @Transactional
    @RequireOwner(resource = ResourceType.FOSTER_REQUEST, idParam = "#requestId", message = "无权限修改此寄养申请")
    public FosterRequestDTO.RequestResponse updateRequest(
            Long userId, Long requestId, FosterRequestDTO.UpdateRequest req) {

        FosterRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> BusinessException.notFound("寄养申请不存在"));

        if (request.getStatus() == FosterRequest.Status.Completed
                || request.getStatus() == FosterRequest.Status.Cancelled) {
            throw BusinessException.badRequest("已完成或已取消的申请不能修改");
        }

        if (req.getFostererId() != null) {
            if (req.getFostererId().equals(userId)) {
                throw BusinessException.badRequest("不能寄养自己的宠物");
            }
            request.setFostererId(req.getFostererId());
        }
        if (req.getStartDate() != null) {
            request.setStartDate(req.getStartDate());
        }
        if (req.getEndDate() != null) {
            request.setEndDate(req.getEndDate());
        }
        if (request.getStartDate().isAfter(request.getEndDate())) {
            throw BusinessException.badRequest("开始日期不能晚于结束日期");
        }
        if (req.getDailyCareNotes() != null) {
            request.setDailyCareNotes(req.getDailyCareNotes());
        }

        request = requestRepository.save(request);
        log.info("寄养申请更新成功: requestId={}", requestId);

        return buildSingleResponse(request);
    }

    @Transactional
    @RequireOwner(resource = ResourceType.FOSTER_REQUEST, role = "PARTICIPANT", idParam = "#requestId", message = "无权限修改此寄养申请状态")
    public FosterRequestDTO.RequestResponse updateStatus(
            Long userId, Long requestId, FosterRequest.Status newStatus) {

        FosterRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> BusinessException.notFound("寄养申请不存在"));

        validateStatusTransition(request.getStatus(), newStatus);

        FosterRequest.Status oldStatus = request.getStatus();
        request.setStatus(newStatus);
        request = requestRepository.save(request);
        log.info("寄养申请状态变更: requestId={}, 旧状态={}, 新状态={}",
                requestId, oldStatus, newStatus);

        Pet pet = petRepository.findById(request.getPetId()).orElse(null);
        String petName = pet != null ? pet.getName() : "宠物";
        User operator = userRepository.findById(userId).orElse(null);
        String operatorName = operator != null ? operator.getUsername() : "某人";

        String statusText = getStatusText(newStatus);
        String title = String.format("寄养申请状态变更：%s", statusText);
        String content = String.format("%s 将宠物「%s」的寄养申请状态更新为「%s」",
                operatorName, petName, statusText);

        List<NotificationEvent.NotificationEntry> entries = new java.util.ArrayList<>();
        if (!request.getOwnerId().equals(userId)) {
            entries.add(NotificationEvent.entry(
                    request.getOwnerId(),
                    Notification.Type.FOSTER_REQUEST_STATUS,
                    title, content,
                    request.getId(),
                    Notification.RelatedType.FOSTER_REQUEST
            ));
        }
        if (request.getFostererId() != null && !request.getFostererId().equals(userId)) {
            entries.add(NotificationEvent.entry(
                    request.getFostererId(),
                    Notification.Type.FOSTER_REQUEST_STATUS,
                    title, content,
                    request.getId(),
                    Notification.RelatedType.FOSTER_REQUEST
            ));
        }
        if (!entries.isEmpty()) {
            eventPublisher.publishEvent(new NotificationEvent(entries));
        }

        return buildSingleResponse(request);
    }

    @Transactional
    @RequireOwner(resource = ResourceType.FOSTER_REQUEST, idParam = "#requestId", message = "无权限删除此寄养申请")
    public void deleteRequest(Long userId, Long requestId) {
        FosterRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> BusinessException.notFound("寄养申请不存在"));

        if (request.getStatus() == FosterRequest.Status.InProgress) {
            throw BusinessException.badRequest("进行中的寄养申请不能删除，请先取消");
        }

        requestRepository.delete(request);
        log.info("寄养申请删除成功: requestId={}, userId={}", requestId, userId);
    }

    private String getStatusText(FosterRequest.Status status) {
        return switch (status) {
            case Pending -> "待确认";
            case Approved -> "已同意";
            case InProgress -> "进行中";
            case Completed -> "已完成";
            case Cancelled -> "已取消";
        };
    }

    private void validateStatusTransition(FosterRequest.Status current, FosterRequest.Status next) {
        boolean valid = switch (current) {
            case Pending -> ALLOWED_FROM_PENDING.contains(next);
            case Approved -> ALLOWED_FROM_APPROVED.contains(next);
            case InProgress -> ALLOWED_FROM_IN_PROGRESS.contains(next);
            case Completed, Cancelled -> false;
        };
        if (!valid) {
            throw BusinessException.badRequest(
                    String.format("无法从状态 %s 变更为 %s", current, next));
        }
    }

    private PageResponse<FosterRequestDTO.RequestResponse> buildPageResponse(Page<FosterRequest> page) {
        List<Long> petIds = page.getContent().stream()
                .map(FosterRequest::getPetId).distinct().toList();
        Set<Long> userIds = page.getContent().stream()
                .flatMap(r -> Stream.of(r.getOwnerId(), r.getFostererId()))
                .filter(id -> id != null)
                .collect(Collectors.toSet());

        Map<Long, Pet> petMap = petRepository.findAllById(petIds).stream()
                .collect(Collectors.toMap(Pet::getId, p -> p));
        Map<Long, User> userMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        List<FosterRequestDTO.RequestResponse> content = page.getContent().stream()
                .map(r -> EntityMapper.toFosterRequestResponse(
                        r,
                        petMap.get(r.getPetId()),
                        userMap.get(r.getOwnerId()),
                        r.getFostererId() != null ? userMap.get(r.getFostererId()) : null
                ))
                .toList();

        return PageResponse.<FosterRequestDTO.RequestResponse>builder()
                .content(content)
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .build();
    }

    private FosterRequestDTO.RequestResponse buildSingleResponse(FosterRequest r) {
        Pet pet = petRepository.findById(r.getPetId()).orElse(null);
        User owner = userRepository.findById(r.getOwnerId()).orElse(null);
        User fosterer = r.getFostererId() != null
                ? userRepository.findById(r.getFostererId()).orElse(null) : null;
        return EntityMapper.toFosterRequestResponse(r, pet, owner, fosterer);
    }

    private Sort parseSort(String sort) {
        if (!StringUtils.hasText(sort)) {
            return Sort.by(Sort.Direction.DESC, "createdAt");
        }
        String[] parts = sort.split(",");
        String field = parts[0];
        Sort.Direction direction = parts.length > 1 && "asc".equalsIgnoreCase(parts[1])
                ? Sort.Direction.ASC : Sort.Direction.DESC;

        return switch (field) {
            case "startDate", "start_date" -> Sort.by(direction, "startDate");
            case "endDate", "end_date" -> Sort.by(direction, "endDate");
            case "status" -> Sort.by(direction, "status");
            case "createdAt", "created_at" -> Sort.by(direction, "createdAt");
            default -> Sort.by(Sort.Direction.DESC, "createdAt");
        };
    }
}
