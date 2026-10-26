package com.demo.logindemo.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

@Data
@EqualsAndHashCode(callSuper = true)
public class AttractionCollectItemResp extends AttractionCardResp {

    private Long collectId;

    private Date collectTime;

    private boolean available;
}
