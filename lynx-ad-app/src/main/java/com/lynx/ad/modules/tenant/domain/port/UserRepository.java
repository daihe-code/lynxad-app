package com.lynx.ad.modules.tenant.domain.port;

import com.lynx.ad.common.domain.Ids.UserId;
import com.lynx.ad.common.domain.PhoneNumber;
import com.lynx.ad.modules.tenant.domain.model.User;
import java.util.Optional;

public interface UserRepository {

    void save(User user);

    Optional<User> findByPhone(PhoneNumber phone);

    Optional<User> findById(UserId id);
}
