package dev.chanler.researcher.interfaces.service;

import dev.chanler.researcher.interfaces.dto.req.GoogleOneTapReqDTO;
import dev.chanler.researcher.interfaces.dto.req.LoginReqDTO;
import dev.chanler.researcher.interfaces.dto.req.RegisterReqDTO;
import dev.chanler.researcher.interfaces.dto.resp.LoginRespDTO;
import dev.chanler.researcher.interfaces.dto.resp.UserInfoRespDTO;

/**
 * @author: Chanler
 */
public interface UserService {

    LoginRespDTO register(RegisterReqDTO req);

    LoginRespDTO login(LoginReqDTO req);

    LoginRespDTO handleGoogleCallback(String code);

    LoginRespDTO handleGoogleOneTap(GoogleOneTapReqDTO req);

    UserInfoRespDTO getUserInfo(Long userId);
}
