package com.petfoster.service;

import com.petfoster.annotation.CheckOwnership;
import com.petfoster.annotation.OwnershipRole;
import com.petfoster.annotation.ResourceType;
import com.petfoster.common.BusinessException;
import com.petfoster.common.PageResponse;
import com.petfoster.dto.DailyLogDTO;
import com.petfoster.entity.FosterDailyLog;
import com.petfoster.entity.FosterRequest;
import com.petfoster.entity.Notification;
import com.petfoster.entity.User;
import com.petfoster.event.NotificationEvent;
import com.petfoster.repository.FosterDailyLogRepository;
import com.petfoster.repository.FosterRequestRepository;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DailyLogService {

    private final FosterDailyLogRepository logRepository;
    private final FosterRequestRepository requestRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final FileStorageService fileStorageService;

    public PageResponse<DailyLogDTO.LogResponse> getLogs(
            int page, int size, String sort,
            Long requestId, Long fostererId, LocalDate startDate, LocalDate endDate) {

        Sort sortObj = parseSort(sort);
        Pageable pageable = PageRequest.of(page, size, sortObj);

        Page<FosterDailyLog> logPage = logRepository.searchLogs(
                requestId, fostererId, startDate, endDate, pageable);

        return buildPageResponse(logPage);
    }

    public DailyLogDTO.LogResponse getLogById(Long id) {
        FosterDailyLog log = logRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("寄养日报不存在"));
        User fosterer = userRepository.findById(log.getFostererId()).orElse(null);
        return EntityMapper.toDailyLogResponse(log, fosterer);
    }

    @Transactional
    public DailyLogDTO.LogResponse createLog(Long userId, DailyLogDTO.CreateLogRequest req) {
        return createLog(userId, req, null);
    }

    @Transactional
    @CheckOwnership(
            resourceType = ResourceType.FOSTER_REQUEST,
            idIndex = 1,
            idField = "requestId",
            userIdIndex = 0,
            role = OwnershipRole.FOSTERER,
            errorMessage = "只有寄养人才能创建日报"
    )
    public DailyLogDTO.LogResponse createLog(Long userId, DailyLogDTO.CreateLogRequest req, org.springframework.web.multipart.MultipartFile[] photoFiles) {
        FosterRequest request = requestRepository.findById(req.getRequestId())
                .orElseThrow(() -> BusinessException.notFound("寄养申请不存在"));

        if (request.getStatus() != FosterRequest.Status.InProgress
                && request.getStatus() != FosterRequest.Status.Approved) {
            throw BusinessException.badRequest("只有已批准或进行中的寄养申请才能创建日报");
        }

        if (req.getLogDate() == null) {
            throw BusinessException.badRequest("日志日期不能为空");
        }

        if (req.getLogDate().isBefore(request.getStartDate())
                || req.getLogDate().isAfter(request.getEndDate())) {
            throw BusinessException.badRequest("日志日期必须在寄养期间内");
        }

        if (logRepository.existsByRequestIdAndLogDate(req.getRequestId(), req.getLogDate())) {
            throw BusinessException.badRequest("该日期的日报已存在，请使用更新接口");
        }

        List<String> uploadedPhotoUrls = new ArrayList<>();
        List<String> photoUrlList = new ArrayList<>();

        try {
            if (StringUtils.hasText(req.getPhotos())) {
                photoUrlList.addAll(List.of(req.getPhotos().split(",")));
            }

            if (photoFiles != null && photoFiles.length > 0) {
                for (org.springframework.web.multipart.MultipartFile photoFile : photoFiles) {
                    if (photoFile != null && !photoFile.isEmpty()) {
                        String photoUrl = fileStorageService.uploadFile(photoFile);
                        photoUrlList.add(photoUrl);
                        uploadedPhotoUrls.add(photoUrl);
                    }
                }
                log_info("日报照片上传完成: 共上传 {} 张照片", uploadedPhotoUrls.size());
            }

            String photos = photoUrlList.isEmpty() ? null : String.join(",", photoUrlList);

            FosterDailyLog log = FosterDailyLog.builder()
                    .requestId(req.getRequestId())
                    .fostererId(userId)
                    .logDate(req.getLogDate())
                    .food(req.getFood())
                    .mood(req.getMood())
                    .photos(photos)
                    .note(req.getNote())
                    .build();

            log = logRepository.save(log);
            log_info("寄养日报创建成功: logId={}, requestId={}, photoCount={}",
                    log.getId(), req.getRequestId(), photoUrlList.size());

            User fosterer = userRepository.findById(userId).orElse(null);
            String fostererName = fosterer != null ? fosterer.getUsername() : "寄养人";

            List<NotificationEvent.NotificationEntry> entries = new ArrayList<>();
            entries.add(NotificationEvent.entry(
                    request.getOwnerId(),
                    Notification.Type.DAILY_LOG_CREATED,
                    "新的寄养日报已发布",
                    String.format("%s 发布了 %s 的寄养日报，请及时查看。",
                            fostererName, req.getLogDate()),
                    log.getId(),
                    Notification.RelatedType.DAILY_LOG
            ));
            entries.add(NotificationEvent.entry(
                    userId,
                    Notification.Type.DAILY_LOG_CREATED,
                    "寄养日报已发布",
                    String.format("您已发布 %s 的寄养日报，请持续记录宠物每日状况。",
                            req.getLogDate()),
                    log.getId(),
                    Notification.RelatedType.DAILY_LOG
            ));
            eventPublisher.publishEvent(new NotificationEvent(entries));

            return EntityMapper.toDailyLogResponse(log, fosterer);
        } catch (Exception e) {
            if (!uploadedPhotoUrls.isEmpty()) {
                for (String photoUrl : uploadedPhotoUrls) {
                    fileStorageService.deleteFile(photoUrl);
                }
                log_warn("数据库操作失败，已清理 {} 个孤儿文件", uploadedPhotoUrls.size());
            }
            throw e;
        }
    }

    private void log_info(String msg, Object... args) {
        log.info(msg, args);
    }

    private void log_warn(String msg, Object... args) {
        log.warn(msg, args);
    }

    @Transactional
    public DailyLogDTO.LogResponse updateLog(Long userId, Long logId, DailyLogDTO.UpdateLogRequest req) {
        return updateLog(userId, logId, req, null, false);
    }

    @Transactional
    @CheckOwnership(
            resourceType = ResourceType.DAILY_LOG,
            idIndex = 1,
            userIdIndex = 0,
            role = OwnershipRole.FOSTERER,
            errorMessage = "无权限修改此日报"
    )
    public DailyLogDTO.LogResponse updateLog(Long userId, Long logId, DailyLogDTO.UpdateLogRequest req,
            org.springframework.web.multipart.MultipartFile[] photoFiles, boolean replacePhotos) {
        FosterDailyLog log = logRepository.findById(logId)
                .orElseThrow(() -> BusinessException.notFound("寄养日报不存在"));

        String oldPhotos = log.getPhotos();
        List<String> uploadedPhotoUrls = new ArrayList<>();

        try {
            if (req.getLogDate() != null) {
                FosterRequest request = requestRepository.findById(log.getRequestId())
                        .orElseThrow(() -> BusinessException.notFound("寄养申请不存在"));
                if (req.getLogDate().isBefore(request.getStartDate())
                        || req.getLogDate().isAfter(request.getEndDate())) {
                    throw BusinessException.badRequest("日志日期必须在寄养期间内");
                }
                if (!req.getLogDate().equals(log.getLogDate())
                        && logRepository.existsByRequestIdAndLogDate(log.getRequestId(), req.getLogDate())) {
                    throw BusinessException.badRequest("该日期的日报已存在");
                }
                log.setLogDate(req.getLogDate());
            }

            if (req.getFood() != null) {
                log.setFood(req.getFood());
            }
            if (req.getMood() != null) {
                log.setMood(req.getMood());
            }
            if (req.getNote() != null) {
                log.setNote(req.getNote());
            }

            List<String> photoUrlList = new ArrayList<>();

            if (!replacePhotos && StringUtils.hasText(oldPhotos)) {
                photoUrlList.addAll(List.of(oldPhotos.split(",")));
            }

            if (req.getPhotos() != null) {
                if (replacePhotos || !StringUtils.hasText(oldPhotos)) {
                    if (StringUtils.hasText(req.getPhotos())) {
                        photoUrlList.addAll(List.of(req.getPhotos().split(",")));
                    }
                } else {
                    if (StringUtils.hasText(req.getPhotos())) {
                        photoUrlList.addAll(List.of(req.getPhotos().split(",")));
                    }
                }
            }

            if (photoFiles != null && photoFiles.length > 0) {
                for (org.springframework.web.multipart.MultipartFile photoFile : photoFiles) {
                    if (photoFile != null && !photoFile.isEmpty()) {
                        String photoUrl = fileStorageService.uploadFile(photoFile);
                        photoUrlList.add(photoUrl);
                        uploadedPhotoUrls.add(photoUrl);
                    }
                }
                log_info("日报照片上传完成: 共上传 {} 张新照片", uploadedPhotoUrls.size());
            }

            String newPhotos = photoUrlList.isEmpty() ? null : String.join(",", photoUrlList);
            log.setPhotos(newPhotos);

            log = logRepository.save(log);
            log_info("寄养日报更新成功: logId={}, photoCount={}", logId, photoUrlList.size());

            if (replacePhotos && StringUtils.hasText(oldPhotos)) {
                String oldPhotosToDelete = oldPhotos;
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        for (String oldPhotoUrl : oldPhotosToDelete.split(",")) {
                            if (StringUtils.hasText(oldPhotoUrl) && oldPhotoUrl.startsWith("/uploads/")) {
                                fileStorageService.deleteFile(oldPhotoUrl.trim());
                            }
                        }
                        log_info("旧日报照片已清理(事务提交后): logId={}, count={}", logId, oldPhotosToDelete.split(",").length);
                    }
                });
            }

            FosterRequest request = requestRepository.findById(log.getRequestId()).orElse(null);
            User fosterer = userRepository.findById(log.getFostererId()).orElse(null);
            String fostererName = fosterer != null ? fosterer.getUsername() : "寄养人";

            if (request != null) {
                List<NotificationEvent.NotificationEntry> entries = new ArrayList<>();
                entries.add(NotificationEvent.entry(
                        request.getOwnerId(),
                        Notification.Type.DAILY_LOG_UPDATED,
                        "寄养日报已更新",
                        String.format("%s 更新了 %s 的寄养日报，请及时查看。",
                                fostererName, log.getLogDate()),
                        log.getId(),
                        Notification.RelatedType.DAILY_LOG
                ));
                entries.add(NotificationEvent.entry(
                        log.getFostererId(),
                        Notification.Type.DAILY_LOG_UPDATED,
                        "寄养日报已更新",
                        String.format("您已更新 %s 的寄养日报，请继续关注宠物状况并及时记录。",
                                log.getLogDate()),
                        log.getId(),
                        Notification.RelatedType.DAILY_LOG
                ));
                eventPublisher.publishEvent(new NotificationEvent(entries));
            }

            return EntityMapper.toDailyLogResponse(log, fosterer);
        } catch (Exception e) {
            if (!uploadedPhotoUrls.isEmpty()) {
                for (String photoUrl : uploadedPhotoUrls) {
                    fileStorageService.deleteFile(photoUrl);
                }
                log_warn("数据库操作失败，已清理 {} 个孤儿文件", uploadedPhotoUrls.size());
            }
            throw e;
        }
    }

    @Transactional
    @CheckOwnership(
            resourceType = ResourceType.DAILY_LOG,
            idIndex = 1,
            userIdIndex = 0,
            role = OwnershipRole.FOSTERER,
            errorMessage = "无权限删除此日报"
    )
    public void deleteLog(Long userId, Long logId) {
        FosterDailyLog log = logRepository.findById(logId)
                .orElseThrow(() -> BusinessException.notFound("寄养日报不存在"));

        String photos = log.getPhotos();

        logRepository.delete(log);
        log_info("寄养日报删除成功: logId={}, userId={}", logId, userId);

        if (StringUtils.hasText(photos)) {
            for (String photoUrl : photos.split(",")) {
                if (StringUtils.hasText(photoUrl) && photoUrl.startsWith("/uploads/")) {
                    fileStorageService.deleteFile(photoUrl.trim());
                }
            }
            log_info("日报照片已清理: logId={}", logId);
        }
    }

    private PageResponse<DailyLogDTO.LogResponse> buildPageResponse(Page<FosterDailyLog> page) {
        List<Long> fostererIds = page.getContent().stream()
                .map(FosterDailyLog::getFostererId).distinct().toList();
        Map<Long, User> userMap = userRepository.findAllById(fostererIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        List<DailyLogDTO.LogResponse> content = page.getContent().stream()
                .map(l -> EntityMapper.toDailyLogResponse(l, userMap.get(l.getFostererId())))
                .toList();

        return PageResponse.<DailyLogDTO.LogResponse>builder()
                .content(content)
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .build();
    }

    private Sort parseSort(String sort) {
        if (!StringUtils.hasText(sort)) {
            return Sort.by(Sort.Direction.DESC, "logDate");
        }
        String[] parts = sort.split(",");
        String field = parts[0];
        Sort.Direction direction = parts.length > 1 && "asc".equalsIgnoreCase(parts[1])
                ? Sort.Direction.ASC : Sort.Direction.DESC;

        return switch (field) {
            case "logDate", "log_date" -> Sort.by(direction, "logDate");
            default -> Sort.by(Sort.Direction.DESC, "logDate");
        };
    }
}
