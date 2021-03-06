package com.chachae.service.impl;

import cn.hutool.core.net.NetUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.chachae.common.core.bean.PageResult;
import com.chachae.common.core.entity.bo.Department;
import com.chachae.common.core.exception.ApiException;
import com.chachae.common.core.utils.DateUtil;
import com.chachae.common.core.utils.LevelCalculateUtil;
import com.chachae.dao.DepartmentDao;
import com.chachae.service.DepartmentService;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.google.common.collect.Lists;
import org.springframework.stereotype.Service;
import tk.mybatis.mapper.entity.Example;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author chachae
 * @date 2019/11/14 9:10
 */
@Service
public class DepartmentServiceImpl implements DepartmentService {

  @Resource private DepartmentDao departmentDao;

  @Override
  public PageResult<Department> queryAll(Integer page, Integer rows) {
    // 添加分页
    PageHelper.startPage(page, rows);
    List<Department> list = this.departmentDao.selectAll();
    // 包装分页结果
    PageInfo<Department> pageInfo = new PageInfo<>(list);
    return new PageResult<>(pageInfo.getPages(), pageInfo.getTotal(), pageInfo.getList());
  }

  @Override
  public PageResult<Department> queryTree(Integer parentId, Integer page, Integer rows) {
    // 计算部门等级
    String level = LevelCalculateUtil.calculateLevel(parentId);
    Example example = new Example(Department.class);
    Example.Criteria criteria = example.createCriteria();
    // 添加分页
    PageHelper.startPage(page, rows);
    if (LevelCalculateUtil.ROOT.equals(level)) {
      criteria.andEqualTo("parentId", LevelCalculateUtil.ROOT);
      List<Department> list = this.departmentDao.selectByExample(example);
      // 包装分页结果
      PageInfo<Department> pageInfo = new PageInfo<>(list);
      return new PageResult<>(pageInfo.getPages(), pageInfo.getTotal(), pageInfo.getList());
    }
    criteria.andEqualTo("level", level);
    List<Department> list = this.departmentDao.selectByExample(example);
    // 通过seq 从小到大排序
    list.sort(Comparator.comparingInt(Department::getSeq));
    // 包装分页结果
    PageInfo<Department> pageInfo = new PageInfo<>(list);
    return new PageResult<>(pageInfo.getPages(), pageInfo.getTotal(), pageInfo.getList());
  }

  @Override
  public List<Department> fuzzyQuery(String name) {
    Example example = new Example(Department.class);
    Example.Criteria criteria = example.createCriteria();
    if (StrUtil.isNotBlank(name)) {
      criteria.andLike("name", "%" + name + "%");
      return this.departmentDao.selectByExample(example);
    }
    return this.departmentDao.selectAll();
  }

  @Override
  public void deleteByPrimaryKey(Integer id) {
    if (isExist(id)) {
      this.departmentDao.deleteByPrimaryKey(id);
    }
  }

  @Override
  public void update(Department department) {
    // 获取数据库中未更新的数据信息
    Department ormData = this.departmentDao.selectByPrimaryKey(department.getId());
    if (ormData.getName().equals(department.getName()) || isExist(department.getName())) {
      // 设置操作信息
      department.setOperateIp(NetUtil.getLocalhostStr());
      department.setOperateTime(DateUtil.nowDateTime());
      this.departmentDao.updateByPrimaryKeySelective(department);
    }
  }

  @Override
  public void add(Department department) {
    // 1. 判断名字是否存在 2. 判断是否为父部门 3. 分支部门则为其设置parentId 计算 level seq
    if (isExist(department.getName())) {
      Integer parentId = department.getParentId();
      // 设置部门等级和seq
      department.setLevel(LevelCalculateUtil.calculateLevel(parentId));
      department.setSeq(calculateSeq(parentId));
      // 设置操作信息
      department.setOperateIp(NetUtil.getLocalhostStr());
      department.setOperateTime(DateUtil.nowDateTime());
      this.departmentDao.insertSelective(department);
    }
  }

  private Integer calculateSeq(Integer parentId) {
    List<Department> list = queryTree(parentId, 1, 5).getItems();
    // parentId = 0 或者list 为空则父部门下无分支部门，直接返回1
    if (parentId == 0 || ObjectUtil.isEmpty(list)) {
      return 1;
    }
    List<Integer> seqList = Lists.newArrayList();
    list.forEach(dept -> seqList.add(dept.getSeq()));
    // 获取最大的seq
    Integer max = Collections.max(seqList);
    return max + 1;
  }

  private boolean isExist(String name) {
    List<Department> list = this.departmentDao.selectAll();
    for (Department dept : list) {
      if (dept.getName().equals(name)) {
        throw ApiException.argError("存在同名部门，不允许添加！");
      }
    }
    return true;
  }

  private boolean isExist(Integer id) {
    Department dept = this.departmentDao.selectByPrimaryKey(id);
    if (ObjectUtil.isEmpty(dept)) {
      throw ApiException.argError("不存在该部门");
    }
    Integer parentId = dept.getParentId();
    List<Department> list = queryTree(id, 1, 5).getItems();
    // 非顶级部门或者顶级部门下不存在分支部门
    if (parentId != 0 || ObjectUtil.isEmpty(list)) {
      return true;
    }
    throw ApiException.argError("该部门下存在二级部门，不允许删除！");
  }
}
