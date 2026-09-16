package io.github.lynxus.it.spring.mapper;

import io.github.lynxus.annotation.Mapper;
import io.github.lynxus.annotation.Insert;
import io.github.lynxus.annotation.Param;
import io.github.lynxus.annotation.Select;

@Mapper
public interface ConsumerUserMapper {

    @Insert("insert into users (id, name) values (#{id}, #{name})")
    int insert(@Param("id") Long id, @Param("name") String name);

    @Select("select id, name from users where id = #{id}")
    ConsumerUser findById(@Param("id") Long id);
}
