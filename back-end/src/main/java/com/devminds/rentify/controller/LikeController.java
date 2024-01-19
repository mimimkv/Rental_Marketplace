package com.devminds.rentify.controller;

import com.devminds.rentify.config.JwtService;
import com.devminds.rentify.dto.LikeDto;
import com.devminds.rentify.service.ItemService;
import com.devminds.rentify.service.LikedItemService;
import com.devminds.rentify.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/rentify/like")
@RequiredArgsConstructor
public class LikeController {

    private final LikedItemService likeService;
    private final JwtService jwtService;
    private final UserService userService;
    private final ItemService itemService;

    @PostMapping("/like")
    public ResponseEntity<String> likeItem(@RequestHeader("Authorization") String token, @RequestBody LikeDto likeDto) {
        try {
            String email = jwtService.extractUsername(token);

            var user = userService.findByEmail(email);

            if (user == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("User not authenticated");
            }
            var item = itemService.getItemById(likeRequest.getItemId());
            if (item == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Item not found");
            }
            likeService.saveLike(user, item);

            return ResponseEntity.ok("Like recorded successfully");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error recording like");
        }
    }
}
