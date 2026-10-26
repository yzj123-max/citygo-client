package com.demo.logindemo.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.demo.logindemo.entity.Ootd;
import com.demo.logindemo.mapper.OotdMapper;
import com.demo.logindemo.service.OotdService;
import org.springframework.stereotype.Service;

@Service
public class OotdServiceImpl extends ServiceImpl<OotdMapper, Ootd> implements OotdService {

}

