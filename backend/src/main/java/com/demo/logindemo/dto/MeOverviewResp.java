package com.demo.logindemo.dto;
import lombok.Data;
import java.util.Map;
@Data
public class MeOverviewResp {
 private Long userId;
 private String nickname;
 private String avatarUrl;
 private Long postCollectCount;
 // 后续模块尚未云端化，null表示暂不提供，不冒充0条。
 private Long attractionCollectCount;
 private Long browseHistoryCount;
 private Long petCount;
 private Long diaryCount;
 private Map<String,String> dataSources = Map.of("postCollections","SERVER","attractionCollections","LOCAL_PENDING_SYNC","browseHistory","LOCAL_PENDING_SYNC","pets","LOCAL_PENDING_SYNC","diaries","LOCAL_PENDING_SYNC");
}
