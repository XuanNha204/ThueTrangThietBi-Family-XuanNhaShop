package vn.xuannhashop.business.rental.application;

import java.util.*;
import vn.xuannhashop.business.rental.domain.RentalChangeDraft;

public interface RentalChangeRepository {
    record Proposal(UUID id, UUID orderId, String status, String requestedBy, String requesterRole,
            String customerAcceptedBy, RentalChangeDraft draft) {}
    void create(UUID id, RentalChangeDraft draft, String actor, String role, String customerAcceptedBy);
    Proposal get(UUID id, boolean lock);
    void consent(UUID id, String customer, String recorder, String note);
    void status(UUID id, String status, String actor, String note);
    Map<String,Object> history(UUID order);
    void apply(UUID id, RentalChangeDraft draft, String actor);
}
