package com.plantcare_backend.controller.WebSocket;

import com.plantcare_backend.dto.chat.ChatMessage;
import com.plantcare_backend.model.Role;
import com.plantcare_backend.model.Users;
import com.plantcare_backend.repository.ChatMessageRepository;
import com.plantcare_backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.AccessDeniedException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@Slf4j
public class ChatController {
    private final UserRepository userRepository;
    private final ChatMessageRepository chatMessageRepository;

    @Autowired
    public ChatController(UserRepository userRepository, ChatMessageRepository chatMessageRepository) {
        this.userRepository = userRepository;
        this.chatMessageRepository = chatMessageRepository;
    }

    @MessageMapping("/chat.sendMessage")
    @SendTo("/topic/vip-community")
    public ChatMessage sendMessage(@Payload ChatMessage chatMessage) throws AccessDeniedException {
        log.info("Received chat message: {}", chatMessage);

        // Validate senderId
        if (chatMessage.getSenderId() == null) {
            throw new IllegalArgumentException("Sender ID cannot be null");
        }

        // Validate content
        if (chatMessage.getContent() == null || chatMessage.getContent().trim().isEmpty()) {
            throw new IllegalArgumentException("Message content cannot be empty");
        }

        Users sender = userRepository.findByIdWithRole(Long.valueOf(chatMessage.getSenderId())).orElseThrow();
        log.info("Sender found: {} with role: {}", sender.getUsername(), sender.getRole().getRoleName());

        // Handle receiverId - can be null for broadcast messages
        Users receiver = null;
        if (chatMessage.getReceiverId() != null) {
            receiver = userRepository.findById(chatMessage.getReceiverId()).orElse(null);
            log.info("Receiver found: {}", receiver != null ? receiver.getUsername() : "null");
        }

        if (!sender.getRole().getRoleName().equals(Role.RoleName.VIP) &&
                !sender.getRole().getRoleName().equals(Role.RoleName.EXPERT)) {
            throw new AccessDeniedException("Chỉ tài khoản VIP hoặc Chuyên gia mới được chat.");
        }

        com.plantcare_backend.model.ChatMessage entity = com.plantcare_backend.model.ChatMessage.builder()
                .sender(sender)
                .receiver(receiver)
                .content(chatMessage.getContent().trim())
                .sentAt(Timestamp.from(Instant.now()))
                .isRead(false)
                .build();

        chatMessageRepository.save(entity);
        log.info("Chat message saved with ID: {}", entity.getMessageId());

        chatMessage.setTimestamp(entity.getSentAt().toInstant().toString());
        chatMessage.setSenderRole(sender.getRole().getRoleName().name());

        log.info("Broadcasting message to /topic/vip-community: {}", chatMessage);
        return chatMessage;
    }

    @GetMapping("/chat/history")
    public List<ChatMessage> getChatHistory() {
        log.info("Fetching chat history...");
        try {
            List<com.plantcare_backend.model.ChatMessage> entities = chatMessageRepository.findAll();
            log.info("Found {} chat messages in database", entities.size());

            if (entities.isEmpty()) {
                log.info("No chat messages found in database");
                return List.of();
            }

            List<ChatMessage> result = entities.stream()
                    .map(entity -> {
                        try {
                            log.debug("Processing entity: ID={}, Sender={}, Content={}", 
                                entity.getMessageId(), 
                                entity.getSender() != null ? entity.getSender().getUsername() : "null",
                                entity.getContent());
                            
                            ChatMessage dto = ChatMessage.builder()
                                    .senderId(entity.getSender() != null ? entity.getSender().getId() : null)
                                    .receiverId(entity.getReceiver() != null ? entity.getReceiver().getId() : null)
                                    .senderRole(entity.getSender() != null && entity.getSender().getRole() != null ? 
                                        entity.getSender().getRole().getRoleName().name() : null)
                                    .content(entity.getContent())
                                    .timestamp(entity.getSentAt() != null ? entity.getSentAt().toInstant().toString() : null)
                                    .build();
                            
                            log.debug("Converted to DTO: {}", dto);
                            return dto;
                        } catch (Exception e) {
                            log.error("Error converting entity to DTO: {}", e.getMessage(), e);
                            return null;
                        }
                    })
                    .filter(dto -> dto != null)
                    .collect(Collectors.toList());

            log.info("Successfully converted {} chat messages to DTOs", result.size());
            return result;
        } catch (Exception e) {
            log.error("Error fetching chat history: {}", e.getMessage(), e);
            return List.of();
        }
    }

    @MessageExceptionHandler
    @SendToUser("/queue/errors")
    public String handleException(Throwable exception) {
        log.error("WebSocket error: ", exception);
        return "Error: " + exception.getMessage();
    }
}