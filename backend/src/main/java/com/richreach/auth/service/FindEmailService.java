package com.richreach.auth.service;

import com.richreach.auth.dto.FindEmailRequest;
import com.richreach.auth.dto.FindEmailResponse;
import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;
import com.richreach.user.entity.User;
import com.richreach.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이름과 전화번호로 가입한 이메일을 찾아 일부를 가려서 알려준다.
 * 계정이 없으면 "계정 없음"으로 알려준다. (화면에 안내 문구가 있다.)
 */
@Service
public class FindEmailService {

    private final UserRepository users;

    public FindEmailService(UserRepository users) {
        this.users = users;
    }

    @Transactional(readOnly = true)
    public FindEmailResponse findMaskedEmail(FindEmailRequest request) {
        User user = users.findByNameAndPhone(request.name().trim(), SignupService.normalizePhone(request.phoneNumber()))
            .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));
        return new FindEmailResponse(EmailMasker.mask(user.getEmail()));
    }

}
