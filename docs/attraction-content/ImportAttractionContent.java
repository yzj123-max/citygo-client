import java.nio.file.*;import java.sql.*;import java.util.*;import com.google.gson.*;
public class ImportAttractionContent {
 static Gson gson=new GsonBuilder().setPrettyPrinting().create();
 static String s(JsonObject o,String k){return o.has(k)&&!o.get(k).isJsonNull()?o.get(k).getAsString():null;}
 static void exec(Connection c,String sql,Object...v)throws Exception{try(var p=c.prepareStatement(sql)){for(int i=0;i<v.length;i++)p.setObject(i+1,v[i]);p.executeUpdate();}}
 static List<Map<String,String>> rows(Connection c,String table,long id)throws Exception{var list=new ArrayList<Map<String,String>>();try(var p=c.prepareStatement("SELECT * FROM "+table+" WHERE "+(table.equals("attractions")?"id":"attraction_id")+"=?")){p.setLong(1,id);try(var r=p.executeQuery()){while(r.next()){var row=new LinkedHashMap<String,String>();for(int j=1;j<=r.getMetaData().getColumnCount();j++)row.put(r.getMetaData().getColumnLabel(j),r.getString(j));list.add(row);}}}return list;}
 static void policy(Connection c,long id,String type,String value,String text,String name,String url,String sourceType)throws Exception{
  exec(c,"INSERT INTO attraction_policy(attraction_id,policy_type,policy_value,content,source_type,source_name,source_url,verified_at,review_due_at,status,create_time,update_time) VALUES(?,?,?,?,?,?,?,NOW(),DATE_ADD(NOW(),INTERVAL 30 DAY),'ACTIVE',NOW(),NOW()) ON DUPLICATE KEY UPDATE policy_value=VALUES(policy_value),content=VALUES(content),source_type=VALUES(source_type),source_name=VALUES(source_name),source_url=VALUES(source_url),verified_at=NOW(),effective_from=NULL,effective_until=NULL,review_due_at=DATE_ADD(NOW(),INTERVAL 30 DAY),status='ACTIVE',update_time=NOW()",id,type,value,text,sourceType,name,url);
 }
 public static void main(String[] args)throws Exception{
  System.setOut(new java.io.PrintStream(System.out,true,java.nio.charset.StandardCharsets.UTF_8));
  Properties p=new Properties();try(var r=Files.newBufferedReader(Path.of("C:/login/login-demo/src/main/resources/application.properties"))){p.load(r);}
  JsonArray entries=JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonArray();
  try(Connection c=DriverManager.getConnection(p.getProperty("spring.datasource.url"),p.getProperty("spring.datasource.username"),p.getProperty("spring.datasource.password"))){
   var backups=new LinkedHashMap<String,Object>();
   // 校验、备份在任何写入之前完成；未知或重复的目标不静默覆盖。
   for(var element:entries){var e=element.getAsJsonObject();if(!java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).toString().equals(s(e,"reviewedDate")))throw new Exception("必须重新核对来源并更新内容审核日期，禁止用旧资料刷新核对时间");if(s(e,"description").length()<150||!s(e,"sourceUrl").startsWith("https://"))throw new Exception("内容未达标");
    long id=0;if(e.has("id"))id=e.get("id").getAsLong();else try(var q=c.prepareStatement("SELECT id FROM attractions WHERE attr_name=? AND city=?")){q.setString(1,s(e,"name"));q.setString(2,s(e,"city"));try(var r=q.executeQuery()){if(r.next())id=r.getLong(1);if(r.next())throw new Exception("重复景点名称");}}
    if(id>0){for(String t:List.of("attractions","attraction_detail","attraction_policy","attraction_image"))backups.put(t+":"+id,rows(c,t,id));e.addProperty("id",id);}
   }
   Path backup=Path.of(args[1]);if(Files.exists(backup))throw new Exception("备份路径已存在");Files.writeString(backup,gson.toJson(backups));
   c.setAutoCommit(false);try{
    for(var element:entries){var e=element.getAsJsonObject();var poi=e.getAsJsonObject("poiData");long id=e.has("id")?e.get("id").getAsLong():0;
     String[] xy=s(poi,"location").split(",");String name=s(e,"name"),city=s(e,"city");
     if(id==0){try(var q=c.prepareStatement("INSERT INTO attractions(attr_name,province,city,level,status,coord_system,sort_weight) VALUES(?,?,?,'','ACTIVE','GCJ02',2000)",Statement.RETURN_GENERATED_KEYS)){q.setString(1,name);q.setString(2,s(poi,"pname"));q.setString(3,city);q.executeUpdate();try(var r=q.getGeneratedKeys()){r.next();id=r.getLong(1);}}}
     else {var old=rows(c,"attractions",id);if(old.size()!=1||!name.equals(old.get(0).get("attr_name")))throw new Exception("目标景点ID名称不一致");}
     exec(c,"UPDATE attractions SET amap_poi_id=?,address=?,longitude=?,latitude=?,coord_system='GCJ02',category_code=?,category_name=?,district=?,adcode=?,sort_weight=2000,poi_synced_at=NOW() WHERE id=?",s(poi,"id"),s(poi,"address"),xy[0],xy[1],s(poi,"typecode"),s(poi,"type"),s(poi,"adname"),s(poi,"adcode"),id);
     exec(c,"INSERT INTO attraction_detail(attraction_id,summary,description,highlights,suggested_duration,traffic_tips,visit_tips,source_name,source_url,verified_at,create_time,update_time) VALUES(?,?,?,?,?,?,?,?,?,NOW(),NOW(),NOW()) ON DUPLICATE KEY UPDATE summary=VALUES(summary),description=VALUES(description),highlights=VALUES(highlights),suggested_duration=VALUES(suggested_duration),traffic_tips=VALUES(traffic_tips),visit_tips=VALUES(visit_tips),source_name=VALUES(source_name),source_url=VALUES(source_url),verified_at=NOW(),update_time=NOW()",id,s(e,"summary"),s(e,"description"),s(e,"highlights"),s(e,"duration"),s(e,"traffic"),s(e,"tips"),s(e,"sourceName"),s(e,"sourceUrl"));
     policy(c,id,"TICKET",s(e,"ticketValue"),s(e,"ticket"),s(e,"ticketSource"),s(e,"ticketUrl"),s(e,"ticketType"));
     policy(c,id,"OPENING_HOURS","SCHEDULED",s(e,"hours"),e.has("hours")&&s(e,"hoursUrl").contains("amap.com")?"高德POI常规资料（需留意临时公告）":"公开参观安排（详情见来源）",s(e,"hoursUrl"),s(e,"hoursUrl").contains("amap.com")?"AMAP":"MANUAL");
     if(e.has("reservation"))policy(c,id,"RESERVATION",s(e,"reservationValue"),s(e,"reservation"),"官方参观指引",s(e,"hoursUrl"),"OFFICIAL");
     int order=0;for(var photo:poi.getAsJsonArray("photos")){String url=s(photo.getAsJsonObject(),"url").replaceFirst("^http://","https://");
      try(var q=c.prepareStatement("SELECT id FROM attraction_image WHERE attraction_id=? AND image_url=?")){q.setLong(1,id);q.setString(2,url);try(var r=q.executeQuery()){if(r.next())continue;}}
      exec(c,"INSERT INTO attraction_image(attraction_id,storage_type,image_url,caption,is_cover,sort_order,source_name,source_url,status,create_time,update_time) VALUES(?,'EXTERNAL',?,?,?,?,?,?,'ACTIVE',NOW(),NOW())",id,url,name+"实景配图",order==0,order,"高德POI","https://www.amap.com/place/"+s(poi,"id"));order++;
     }
     System.out.println("PREPARED "+id+" "+name+" description="+s(e,"description").length());
    }c.commit();System.out.println("COMMITTED "+entries.size());
   }catch(Exception ex){c.rollback();System.out.println("ROLLED BACK");throw ex;}
  }
 }
}

