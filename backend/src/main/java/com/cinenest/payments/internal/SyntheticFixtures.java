package com.cinenest.payments.internal;
import java.io.ByteArrayOutputStream;
import java.util.Set;
/** Closed, visibly watermarked fixtures. Configuration cannot allowlist arbitrary real documents. */
final class SyntheticFixtures {
    private SyntheticFixtures(){}
    static byte[] image(String format){
        try{var img=new java.awt.image.BufferedImage(320,120,java.awt.image.BufferedImage.TYPE_INT_RGB);var g=img.createGraphics();g.setColor(java.awt.Color.WHITE);g.fillRect(0,0,320,120);g.setColor(java.awt.Color.BLACK);g.drawString("SYNTHETIC RECEIPT - NO REAL MONEY",10,40);g.drawString("TEST ONLY / NOT BANK CREDIT",10,70);g.dispose();var out=new ByteArrayOutputStream();if(!javax.imageio.ImageIO.write(img,format,out))throw new IllegalStateException();return out.toByteArray();}catch(Exception unavailable){throw new BankFailure(503,"Synthetic fixtures unavailable");}
    }
    static Set<String> hashes(){return Set.of(BankPolicy.hash(image("png")),BankPolicy.hash(image("jpeg")));}
    // Explicit test operator utility. Only synthetic bytes; no application endpoint or public document URL.
    public static void main(String[] args)throws Exception{if(args.length!=1)throw new IllegalArgumentException("Provide an outside-repository test fixture directory");var root=java.nio.file.Path.of(args[0]);var repo=java.nio.file.Path.of("").toAbsolutePath().normalize();while(repo.getParent()!=null&&!java.nio.file.Files.exists(repo.resolve("AGENTS.md")))repo=repo.getParent();if(!root.isAbsolute()||root.normalize().startsWith(repo))throw new IllegalArgumentException("Outside-repository absolute path required");java.nio.file.Files.createDirectories(root);for(String format:java.util.List.of("png","jpeg")){byte[] bytes=image(format);java.nio.file.Files.write(root.resolve("SYNTHETIC-NO-REAL-MONEY."+format),bytes,java.nio.file.StandardOpenOption.CREATE_NEW);System.out.println(format+" SHA-256 "+BankPolicy.hash(bytes));}}
}
