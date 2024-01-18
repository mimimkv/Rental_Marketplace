package com.devminds.rentify.auth;

import com.devminds.rentify.configuration.JwtService;
import com.devminds.rentify.entity.Address;
import com.devminds.rentify.entity.Role;
import com.devminds.rentify.entity.User;
import com.devminds.rentify.enums.UserRole;
import com.devminds.rentify.exception.UserNotFoundException;
import com.devminds.rentify.repository.AddressRepository;
import com.devminds.rentify.repository.RoleRepository;
import com.devminds.rentify.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.security.core.AuthenticationException;


import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class
AuthenticationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final UserMapper userMapper;
    private final AddressMapper addressMapper;
    private final RoleRepository roleRepository;


    public AuthenticationRespone register(UserRegisterDto userRegisterDto) {


        var role = new Role(UserRole.USER);
        roleRepository.save(role);

        User user = userMapper.mapToUser(userRegisterDto);

        if (user.getPassword().equals(userRegisterDto.getConfirmPassword())) {
            user.setPassword(passwordEncoder.encode(userRegisterDto.getPassword()));

            user.setRole(role);

            userRepository.save(user);
        }


        var token = jwtService.generateToken(user);
        return AuthenticationRespone.builder()
                .token(token)
                .email(user.getEmail()).build();
    }

    private List<Address> convertToAddressEntities(List<AddressDto> addressDto) {
        if (addressDto == null) {
            return Collections.emptyList();  // or return null, or handle it according to your requirements
        }
       
        return addressDto.stream()
                .map(addressMapper::mapToAddress)
                .collect(Collectors.toList());
    }


    public AuthenticationRespone login(LoginDto loginDto) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginDto.getEmail(), loginDto.getPassword())
            );

            var user = userRepository.findByEmail(loginDto.getEmail())
                    .orElseThrow(() -> new UserNotFoundException("User not found"));

            var token = jwtService.generateToken(user);
            return AuthenticationRespone.builder()
                    .token(token)
                    .email(user.getEmail())
                    .build();
        } catch (AuthenticationException e) {
            throw new BadCredentialsException("Invalid email or password");
        }
    }
}
