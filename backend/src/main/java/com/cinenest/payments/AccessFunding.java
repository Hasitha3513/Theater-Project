package com.cinenest.payments;
import java.util.UUID;
/** Current independently verified allocated synthetic-bank funding, not uploaded receipt evidence. */
public interface AccessFunding {boolean eligible(UUID request,UUID owner,UUID venue,long due,String currency);}
