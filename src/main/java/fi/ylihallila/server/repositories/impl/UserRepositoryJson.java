package fi.ylihallila.server.repositories.impl;

import fi.ylihallila.server.util.Constants;
import fi.ylihallila.server.Util;
import fi.ylihallila.server.models.User;
import fi.ylihallila.server.repositories.AbstractJsonRepository;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public class UserRepositoryJson extends AbstractJsonRepository<User> {

    public UserRepositoryJson() {
        super(Path.of(Constants.USERS_FILE), Util.getMapper().getTypeFactory().constructParametricType(List.class, User.class));
    }

    @Override
    public Optional<User> getById(String id) {
        for (User user : getData()) {
            if (user.getId().equalsIgnoreCase(id)) {
                return Optional.of(user);
            }
        }

        return Optional.empty();
    }

    @Override
    public void update(User updated) {
        User oldUser = getById(updated.getId()).orElseThrow();

        oldUser.setName(updated.getName());
        oldUser.setRoles(updated.getRoles());

        commit();
    }
}
