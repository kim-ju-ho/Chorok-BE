package com.finalproject.chorok.login.service;


import com.finalproject.chorok.common.utils.PlantUtils;
import com.finalproject.chorok.common.utils.RedisUtil;
import com.finalproject.chorok.login.dto.*;

import com.finalproject.chorok.login.model.EmailMessage;
import com.finalproject.chorok.login.model.Labeling;
import com.finalproject.chorok.login.model.User;
import com.finalproject.chorok.login.repository.EmailService;
import com.finalproject.chorok.login.repository.LabelingRepository;
import com.finalproject.chorok.login.repository.UserRepository;
import com.finalproject.chorok.login.validator.Validator;
import com.finalproject.chorok.plant.model.Plant;
import com.finalproject.chorok.plant.repository.PlantRepository;
import com.finalproject.chorok.login.dto.LabelingResponseDto;
import com.finalproject.chorok.post.utils.CommUtils;
import com.finalproject.chorok.security.UserDetailsImpl;
import com.finalproject.chorok.security.jwt.JwtTokenUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import javax.activity.InvalidActivityException;
import javax.servlet.http.HttpServletRequest;
import javax.transaction.Transactional;
import java.util.*;


@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final TemplateEngine templateEngine;
    private final HttpServletRequest request;
    private final Validator validator;
    private final LabelingRepository labelingRepository;
    private final RedisUtil redisUtil;
    private final PlantRepository plantRepository;
    private final PlantUtils plantUtils;
    private final CommUtils commUtils;
    private final KakaoUserService kakaoUserService;

    @Transactional
    public HashMap<String, String> registerUser(SignupRequestDto requestDto) {
        String msg = "회원인증 이메일 전송";
        try {
            //회원가입 확인
            validator.signupValidate(requestDto);
        } catch (IllegalArgumentException e) {
            msg = e.getMessage();
            return commUtils.errResponseHashMap(HttpStatus.BAD_REQUEST);
        }

        // 패스워드 암호화
        String username = requestDto.getUsername();
        String password = passwordEncoder.encode(requestDto.getPassword());
        String nickname = requestDto.getNickname();

        String emailCheckToken = UUID.randomUUID().toString();
        String profileImgUrl = requestDto.getProfileImgUrl();
        User user = new User(username, password, nickname, emailCheckToken, profileImgUrl);

        //이메일 인증 코드부분
        redisUtil.set(username, user, 3);

        sendSignupConfirmEmail(user);

        // 이메일 인증 생략하고 회원가입(추후 삭제)
//        User savedUser = userRepository.save(user);
//        Labeling defaultLabeling = new Labeling(savedUser);
//        labelingRepository.save(defaultLabeling);
        return commUtils.responseHashMap(HttpStatus.OK);
    }

    @Transactional
    public CMResponseDto sendPasswordResetLink(EmailRequestDto emailRequestDto) throws InvalidActivityException {
        User findUser = userRepository.findByUsername(emailRequestDto.getEmail()).orElseThrow(
                () -> new InvalidActivityException("존재하지 않는 이메일입니다.")
        );

        String emailCheckToken = UUID.randomUUID().toString();
//        redisUtil.set(emailCheckToken, findUser, 3 );

        sendPwResetEmail(findUser, emailCheckToken);
        return new CMResponseDto("true");
    }


    private void sendSignupConfirmEmail(User user) {
        String path = "https://chorok.kr";

        Context context = new Context();
        context.setVariable("link", path + "/signup/emailValidation?token=" + user.getEmailCheckToken() +
                "&email=" + user.getUsername());
        String message = templateEngine.process("email-link", context);
        EmailMessage emailMessage = EmailMessage.builder()
                .to(user.getUsername())
                .subject("초록(Chorok), 회원 가입 인증 메일")
                .message(message)
                .build();
        emailService.sendEmail(emailMessage);
    }

    private void sendPwResetEmail(User user, String emailCheckToken) {
        String path = "https://chorok.kr";

        Context context = new Context();
        context.setVariable("link2", path + "/changepwd?token=" + emailCheckToken +
                "&email=" + user.getUsername());
        String message = templateEngine.process("reset-password-link", context);
        EmailMessage emailMessage = EmailMessage.builder()
                .to(user.getUsername())
                .subject("초록(Chorok), 비밀번호 재설정 메일")
                .message(message)
                .build();
        emailService.sendEmail(emailMessage);
        redisUtil.set(user.getUsername(), emailCheckToken, 3);
    }

    //로그인 확인
    public IsLoginDto isloginChk(UserDetailsImpl userDetails) {
        String username = userDetails.getUsername();
        String nickname = userDetails.getUser().getNickname();
        Long userId = userDetails.getUser().getUserId();
        String profileImgUrl = userDetails.getUser().getProfileImageUrl();
        String profileMsg = userDetails.getUser().getProfileMsg();
        Optional<User> user = userRepository.findByUsername(username);
        IsLoginDto isLoginDto = IsLoginDto.builder()
                .username(username)
                .nickname(nickname)
                .userId(userId)
                .profileImgUrl(profileImgUrl)
                .profileMsg(profileMsg)
                .build();
        return isLoginDto;

    }


    //아이디 중복체크
    public String usernameDuplichk(DuplicateChkDto duplicateChkDto) {
        String msg = "사용가능한 이메일 입니다.";

        try {
            validator.idCheck(duplicateChkDto);
        } catch (IllegalArgumentException e) {
            msg = e.getMessage();
            return msg;
        }
        return msg;
    }


    //닉네임 중복검사
    public String nicknameDuplichk(DuplicateChkDto duplicateChkDto) {
        String msg = "사용가능한 닉네임 입니다.";

        try {
            validator.nickCheck(duplicateChkDto);
        } catch (IllegalArgumentException e) {
            msg = e.getMessage();
            return msg;
        }
        return msg;
    }

    @Transactional
    public UserResponseDto checkEmailToken(String token, String email) throws InvalidActivityException {

        User findUser = (User) redisUtil.get(email);

        if (!findUser.isValidToken(token))
            throw new InvalidActivityException("유효한 토큰이 아닙니다.");

        Optional<User> existingUser = userRepository.findByUsername(findUser.getUsername());
        if(existingUser.isPresent())
            throw new InvalidActivityException("이미 등록된 유저입니다.");

        User savedUser = userRepository.save(findUser);
        Labeling defaultLabeling = new Labeling(savedUser);
        labelingRepository.save(defaultLabeling);
        if (savedUser.getUserId() > 0) redisUtil.delete(email);

        // 4. 강제 로그인 처리
        final String AUTH_HEADER = "Authorization";
        final String TOKEN_TYPE = "BEARER";
        String jwt_token = forceLogin(savedUser); // 로그인처리 후 토큰 받아오기
        HttpHeaders headers = new HttpHeaders();
        headers.set(AUTH_HEADER, TOKEN_TYPE + " " + jwt_token);
        UserResponseDto userResponseDto = UserResponseDto.builder()
                .token(TOKEN_TYPE + " " + jwt_token)
                .userId(savedUser.getUserId())
                .nickname(savedUser.getNickname())
                .email(savedUser.getUsername())
                .build();
        return userResponseDto;

    }

    @Transactional
    public HashMap<String, String> checkPasswordResetEmailToken(PasswordResetDto passwordResetDto) throws InvalidActivityException {

        String email = passwordResetDto.getEmail();
        String token = passwordResetDto.getToken();
        String password = passwordEncoder.encode(passwordResetDto.getNewPassword());
        String emailCheckToken = (String) redisUtil.get(email);
        if (!token.equals(emailCheckToken))
            throw new InvalidActivityException("유효한 토큰이 아닙니다.");

        Optional<User> user = userRepository.findByUsername(email);
        if(user.isPresent()){
            user.get().changeEmailChkToken(token);
            user.get().changePassword(password);
            userRepository.save(user.get());
            redisUtil.delete(email);

            return commUtils.responseHashMap(HttpStatus.OK);
        } throw new InvalidActivityException("오류입니다.");
            }

    @Transactional
    public String updateLabeling(LabelingDto labelingDto, UserDetailsImpl userDetails) {
        String msg = "레이블링 업데이트 성공";
        Labeling labeling = labelingRepository.findByUser(userDetails.getUser()).orElseThrow(
                () -> new IllegalArgumentException("레이블링 오류입니다.")
        );
        labeling.update(labelingDto);
        return msg;
    }

    @Transactional
    public LabelingResponseDto getLabelingPlant(LabelingDto labelingDto) {
        Optional<Plant> labeledPlant = getLabelingTest(labelingDto.getAnswer1(), labelingDto.getAnswer2(), labelingDto.getAnswer3(), labelingDto.getAnswer4());
        boolean isResult = true;
        if (!labeledPlant.isPresent() && labelingDto.getAnswer1().equals("pl03")) {
            labeledPlant = plantRepository.searchOnePlantByLabeling("pl02", labelingDto.getAnswer2(), labelingDto.getAnswer3(), labelingDto.getAnswer4());
            if (!labeledPlant.isPresent()){
                labeledPlant = plantRepository.searchOnePlantByLabeling("pl01", labelingDto.getAnswer2(), labelingDto.getAnswer3(), labelingDto.getAnswer4());
            }
        }
        if (!labeledPlant.isPresent() && labelingDto.getAnswer1().equals("pl02")) {
            labeledPlant = plantRepository.searchOnePlantByLabeling("pl01", labelingDto.getAnswer2(), labelingDto.getAnswer3(), labelingDto.getAnswer4());
        }
        if (!labeledPlant.isPresent()) {
            labeledPlant = plantRepository.searchOneRandomPlantByLabeling();
            isResult = false;
        }
        return new LabelingResponseDto(
                    labeledPlant.get().getPlantNo(),
                    plantUtils.getPlantThumbImg(labeledPlant.get().getPlantNo()),
                    labeledPlant.get().getPlantName(),
                    isResult);

    }

    @Transactional
    public List<LabelingResponseDto> getLabelingResults(UserDetailsImpl userDetails) {
        Optional<Labeling> labelingTested = labelingRepository.findByUser_UserId(userDetails.getUserId());
        if (labelingTested.isPresent()) {
            List<Plant> labeledPlants = plantRepository.searchThreePlantByLabeling(
                    labelingTested.get().getAnswer1(),
                    labelingTested.get().getAnswer2(),
                    labelingTested.get().getAnswer3(),
                    labelingTested.get().getAnswer4());

            List<LabelingResponseDto> labelingResponseDtos = new ArrayList<>();

            for (Plant labeledPlant : labeledPlants) {

                LabelingResponseDto labelingResponseDto = new LabelingResponseDto(
                        labeledPlant.getPlantNo(),
                        plantUtils.getPlantThumbImg(labeledPlant.getPlantNo()),
                        labeledPlant.getPlantName(),
                        true
                );
                labelingResponseDtos.add(labelingResponseDto);

            }
            return labelingResponseDtos;
        } else {
            return null;
        }
    }

    public String forceLogin(User user) {
        UserDetailsImpl userDetails = new UserDetailsImpl(user);
        Authentication authentication = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        return JwtTokenUtils.generateJwtToken(userDetails);
    }

    @Transactional
    public Optional<Plant> getLabelingTest(String answer1, String answer2, String answer3, String answer4) {
        return plantRepository.searchOnePlantByLabeling(answer1, answer2, answer3, answer4);
    }
}
