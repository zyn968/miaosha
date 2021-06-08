package com.changgou.user.dao;

import com.changgou.user.pojo.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TbUserMapper extends tk.mybatis.mapper.common.Mapper<User> {


    /*    *
     * 根据用户名查询用户的信息
     * @param username
     * @return*/

    User findOne(String username);
}
