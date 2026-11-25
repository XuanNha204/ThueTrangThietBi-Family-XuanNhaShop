package vn.xuannhashop.infrastructure.persistence;

import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import vn.xuannhashop.business.identity.application.GmailChallengeRepository;
import vn.xuannhashop.business.identity.application.GmailChallengeService;
import static vn.xuannhashop.infrastructure.persistence.Sql.p;

@Repository
public class JdbcGmailChallengeRepository implements GmailChallengeRepository {
    private final Sql sql;

    public JdbcGmailChallengeRepository(Sql sql) {
        this.sql = sql;
    }

    @Override
    public boolean reserveSend(String inbox) {
        sql.update("delete from gmail_mail_limit where last_sent < now() - interval '1 day'", Map.of());
        return sql.update("""
            insert into gmail_mail_limit (inbox_digest, window_started, last_sent, sends)
            values (:inbox, now(), now(), 1)
            on conflict (inbox_digest) do update set
                window_started = case when gmail_mail_limit.window_started <= now() - interval '15 minutes'
                                      then now() else gmail_mail_limit.window_started end,
                last_sent = now(),
                sends = case when gmail_mail_limit.window_started <= now() - interval '15 minutes'
                             then 1 else gmail_mail_limit.sends + 1 end
            where gmail_mail_limit.last_sent <= now() - (:delay * interval '1 second')
              and (gmail_mail_limit.sends < 5 or gmail_mail_limit.window_started <= now() - interval '15 minutes')
            """, p("inbox", inbox, "delay", GmailChallengeService.RESEND_SECONDS)) == 1;
    }

    @Override
    public boolean activate(UUID id, byte[] digest) {
        sql.update("delete from gmail_login_challenge where expires_at < now() - interval '30 minutes'", Map.of());
        return sql.update("""
            insert into gmail_login_challenge(id, digest, expires_at)
            values (:id, :digest, now() + (:ttl * interval '1 second'))
            on conflict (id) do update set digest = excluded.digest, expires_at = excluded.expires_at, attempts_left = 5
            where not gmail_login_challenge.consumed
            """, p("id", id, "digest", digest, "ttl", GmailChallengeService.CODE_TTL_SECONDS)) == 1;
    }

    @Override
    public Check check(UUID id, byte[] digest) {
        var row = sql.one("""
            update gmail_login_challenge
            set attempts_left = attempts_left - 1, consumed = (digest=:digest)
            where id=:id and not consumed and attempts_left>0 and expires_at>now()
            returning consumed, attempts_left
            """, p("id", id, "digest", digest));
        return row == null ? null : new Check((Boolean) row.get("consumed"),
            ((Number) row.get("attempts_left")).intValue());
    }
}
