package vn.xuannhashop.business.identity.application;

import java.util.UUID;

public interface GmailChallengeRepository {
    record Check(boolean accepted, int remaining) {}
    boolean reserveSend(String inboxDigest);
    boolean activate(UUID id, byte[] digest);
    Check check(UUID id, byte[] digest);
}
