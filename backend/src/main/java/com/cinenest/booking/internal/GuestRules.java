package com.cinenest.booking.internal;
import java.text.Normalizer;import java.util.Locale;
final class GuestRules {
 private GuestRules(){}
 static String label(Object input){
  if(!(input instanceof String text)||text.length()>64)throw new InventoryFailure(400,"Use a synthetic numeric label such as SYNTHETIC-1");
  String value=Normalizer.normalize(text,Normalizer.Form.NFKC).strip().toUpperCase(Locale.ROOT);
  if(!value.matches("SYNTHETIC-[1-9][0-9]{0,5}"))throw new InventoryFailure(400,"Use SYNTHETIC-1 to SYNTHETIC-999999; no personal information");
  return value;
 }
}
