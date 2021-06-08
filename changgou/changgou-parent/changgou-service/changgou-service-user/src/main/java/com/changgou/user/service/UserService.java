package com.changgou.user.service;

import com.changgou.user.pojo.Address;
import com.changgou.user.pojo.User;
import com.github.pagehelper.PageInfo;

import java.util.List;


public interface UserService {

    /***
     * User多条件分页查询
     * @param user
     * @param page
     * @param size
     * @return
     */
    PageInfo<User> findPage(User user, int page, int size);

    /***
     * User分页查询
     * @param page
     * @param size
     * @return
     */
    PageInfo<User> findPage(int page, int size);

    /***
     * User多条件搜索方法
     * @param user
     * @return
     */
    List<User> findList(User user);

    /***
     * 删除User
     * @param id
     */
    void delete(String id);

    /***
     * 修改User数据
     * @param user
     */
    void update(User user);

    /***
     * 新增User
     * @param user
     */
    void add(User user);

    /**
     * 根据ID查询User
     * @param id
     * @return
     */
     User findById(String id);

    /**
     * 根据用户名查询User
     * @param username
     * @return
     */
    User findone(String username);

    /***
     * 查询所有User
     * @return
     */
    List<User> findAll();


    /**
     * 增加积分
     * @param points
     * @param username
     * @return
     */
    public int addPoints(Integer points ,String username);




}
