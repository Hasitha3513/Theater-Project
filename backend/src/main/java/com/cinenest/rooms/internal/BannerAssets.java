package com.cinenest.rooms.internal;
import java.util.*;import org.springframework.stereotype.Component;
@Component
class BannerAssets {
 record Asset(String id,int version,String path,String sha256,int bytes){public int width(){return 960;}public int height(){return 540;}public String mime(){return "image/webp";}}
 static final List<Asset> ALL=List.of(
new Asset("hero",1,"/cinema/hero-960.webp","079fd9168cda6d270f477dfca1f94938e64474b8946bab70b5a8d311bc6cd069",46918),
new Asset("violet",1,"/cinema/violet-960.webp","f009496a5fb02907bf554c5e9706b6be3f222f5e7595c788b48aa010ea919879",49736),
new Asset("blue",1,"/cinema/blue-960.webp","5515c0fcf888360dcee04c78aea4043b54421e68abaf84dbd9d966db314394c8",63922),
new Asset("amber",1,"/cinema/amber-960.webp","3083d63321771b841370e4cdbdbe238c1454fc6b057f143cd81e28db5dd8f15f",57028),
new Asset("recliners",1,"/cinema/recliners-960.webp","66f73b4dfe80e35eef4e39d7a510b289b9597fd0da4a48a9c7762b1510a5d131",63154));
 Asset resolve(String id){return ALL.stream().filter(a->a.id().equals(id)).findFirst().orElseThrow(()->new RoomFailure(400,"Unknown approved illustration"));}
}
