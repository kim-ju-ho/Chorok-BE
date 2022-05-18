package com.finalproject.chorok.mypage.service;

import com.finalproject.chorok.common.Image.S3Uploader;
import com.finalproject.chorok.login.dto.DuplicateChkDto;
import com.finalproject.chorok.login.model.User;
import com.finalproject.chorok.login.repository.UserRepository;
import com.finalproject.chorok.login.validator.Validator;
import com.finalproject.chorok.myPlant.dto.MyAllPlantDetailResponseDto;
import com.finalproject.chorok.myPlant.model.MyPlant;
import com.finalproject.chorok.myPlant.repository.MyPlantRepository;
import com.finalproject.chorok.mypage.model.PlantBookMark;
import com.finalproject.chorok.mypage.repository.PlantBookMarkRepository;
import com.finalproject.chorok.plant.repository.PlantRepository;
import com.finalproject.chorok.post.model.PostBookMark;
import com.finalproject.chorok.post.repository.PostRepository;
import com.finalproject.chorok.post.utils.CommUtils;
import com.finalproject.chorok.security.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.transaction.Transactional;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MypageService {
    private final PlantBookMarkRepository plantBookMarkRepository;
    private final CommUtils commUtils;
    private final PostRepository postRepository;
    private final MyPlantRepository myPlantRepository;
    private final PlantRepository plantRepository;
    private final Validator validator;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final S3Uploader s3Uploader;



    // 식물 북마크
    public HashMap<String, String> plantBookMark(Long plantNo, User user) {
        if(commUtils.getPlantBookMark(user,plantNo)!=null){
            // 북마크 삭제
            plantBookMarkRepository.deleteByUserPlantBookMarkQuery(user.getUserId(),plantNo);
            return commUtils.toggleResponseHashMap(false);

        }else{
            // 북마크 추가
            plantBookMarkRepository.save(new PlantBookMark(user,commUtils.getPlant(plantNo)));
            return commUtils.toggleResponseHashMap(true);
        }
    }
    // 내가 북마크한 게시물
    public void myPostBookMark(UserDetailsImpl userDetails) {
       // p
    }

    public void myPhoto(UserDetailsImpl userDetails) {
    }

    public List<MyAllPlantDetailResponseDto> getAllMyPlantDetail(UserDetailsImpl userDetails) {
        List<MyPlant> myPlants = myPlantRepository.findAllByUser(userDetails.getUser());
        List<MyAllPlantDetailResponseDto> myAllPlantDetailResponseDtos = new ArrayList<>();
        for (MyPlant myPlant : myPlants) {
            MyAllPlantDetailResponseDto myAllPlantDetailResponseDto = new MyAllPlantDetailResponseDto(
                    myPlant.getMyPlantNo(),
                    myPlant.getMyPlantImgUrl(),
                    myPlant.getMyPlantPlace(),
                    myPlant.getMyPlantName(),
                    plantRepository.findByPlantNo(myPlant.getPlantNo()).getPlantName(),
                    myPlant.getStartDay(),
                    myPlant.getEndDay()
            );
            myAllPlantDetailResponseDtos.add(myAllPlantDetailResponseDto);
        }

        return myAllPlantDetailResponseDtos;
    }

    //비밀번호 수정
    public HashMap<String, String> updatePassword(String password, UserDetailsImpl userDetails) {
        User user = userDetails.getUser();
        validator.passwordCheck(password);
        String encodedPassword = passwordEncoder.encode(password);
        user.changePassword(encodedPassword);
        userRepository.save(user);
        return commUtils.responseHashMap(HttpStatus.OK);
    }

    //프로필 닉네임, 사진 수정
    @Transactional
    public HashMap<String, String> updateProfile(String nickname, MultipartFile multipartFile, UserDetailsImpl userDetails) throws IOException {

        String profileImgUrl = null;
        User user = userDetails.getUser();

        if (nickname != null) {
            if (!nickname.equals(userDetails.getNickname())) {
                validator.nickCheck(new DuplicateChkDto(nickname));
                user.changeNickname(nickname);
                userRepository.save(user);
            }
        }
        if(multipartFile != null){
            profileImgUrl = s3Uploader.updateProfileImage(multipartFile, "static", userDetails);
            user.changeProfileImage(profileImgUrl);
            userRepository.save(user);
        }

        return commUtils.responseHashMap(HttpStatus.OK);
    }

}
