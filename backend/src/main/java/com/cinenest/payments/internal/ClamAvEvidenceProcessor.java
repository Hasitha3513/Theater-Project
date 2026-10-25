package com.cinenest.payments.internal;

/** Real local scan of admitted synthetic bytes only. Never a funds or document-authenticity decision. */
final class ClamAvEvidenceProcessor implements BankEvidenceProcessor {
    private final SyntheticEvidenceProcessor synthetic;
    private final ClamAvSocketClient scanner;
    ClamAvEvidenceProcessor(SyntheticEvidenceProcessor synthetic,ClamAvSocketClient scanner){this.synthetic=synthetic;this.scanner=scanner;}
    public void admit(byte[] bytes,String mime,BankPolicy.Policy policy){synthetic.process(bytes,mime,policy);}
    public Result process(byte[] bytes,String mime,BankPolicy.Policy policy){
        // Fixture admission cannot be bypassed by replacing the malware scanner.
        var result=synthetic.process(bytes,mime,policy);scanner.scan(bytes);return result;
    }
}
