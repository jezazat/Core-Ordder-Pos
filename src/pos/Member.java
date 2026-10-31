package pos;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public class Member extends Customer {
    private final String memberId;
    private String memberName, memberPhone;
    private int memberPointBalance;
    private final List<PointTransaction> memberPointHistory = new ArrayList<>();
    public Member(String customerId, String memberId, String name, String phone) {
        super(customerId);
        if (memberId == null || memberId.isBlank()) throw new IllegalArgumentException("รหัสสมาชิกห้ามว่าง");
        this.memberId = memberId;
        adjustMember(name, phone);
    }
    public String memberId() { return memberId; }
    @Override public String name() { return memberName; }
    public String phone() { return memberPhone; }
    public int points() { return memberPointBalance; }
    void adjustMember(String name, String phone) {
        if (name == null || name.isBlank() || phone == null || !phone.matches("[0-9]{10}"))
            throw new IllegalArgumentException("ชื่อห้ามว่างและเบอร์โทรต้องเป็นตัวเลข 10 หลัก");
        memberName = name.trim(); memberPhone = phone;
    }
    void validateDelta(int delta) {
        long next = (long) memberPointBalance + delta;
        if (next < 0 || next > Integer.MAX_VALUE) throw new IllegalArgumentException("คะแนนไม่เพียงพอหรือเกินขอบเขต");
    }
    private void record(int delta, PointTransaction.Type type, String orderId, String reason, LocalDateTime at) {
        validateDelta(delta);
        if (reason == null || reason.isBlank() || at == null) throw new IllegalArgumentException("ต้องระบุเหตุผลและเวลาของคะแนน");
        if (orderId != null && memberPointHistory.stream().anyMatch(t -> orderId.equals(t.orderId()) && type == t.type()))
            throw new IllegalArgumentException("บันทึกคะแนนของออร์เดอร์นี้แล้ว");
        if (delta == 0) return;
        memberPointBalance += delta;
        memberPointHistory.add(new PointTransaction(type, delta, memberPointBalance, at, orderId, reason.trim()));
    }
    public static int earnedFor(BigDecimal amountSpent) {
        if (amountSpent == null || amountSpent.signum() < 0) throw new IllegalArgumentException("ยอดใช้จ่ายไม่ถูกต้อง");
        try { return amountSpent.divideToIntegralValue(BigDecimal.TEN).intValueExact(); }
        catch (ArithmeticException ex) { throw new IllegalArgumentException("ยอดคะแนนเกินขอบเขต"); }
    }
    int addPointsMember(BigDecimal amountSpent, String orderId, String reason, LocalDateTime at) {
        int points = earnedFor(amountSpent);
        record(points, PointTransaction.Type.EARN, orderId, reason, at);
        return points;
    }
    void redeemPointsMember(int points, String orderId, String reason, LocalDateTime at) {
        if (points < 0 || points % 10 != 0) throw new IllegalArgumentException("ใช้คะแนนเป็นชุดละ 10");
        record(-points, PointTransaction.Type.REDEEM, orderId, reason, at);
    }
    void returnPointsMember(int points, String orderId, String reason, LocalDateTime at) {
        if (points < 0) throw new IllegalArgumentException("คะแนนคืนต้องไม่ติดลบ");
        int redeemed = memberPointHistory.stream()
                .filter(t -> Objects.equals(orderId, t.orderId()) && t.type() == PointTransaction.Type.REDEEM)
                .mapToInt(t -> -t.points()).sum();
        if (points != redeemed) throw new IllegalArgumentException("คะแนนคืนไม่ตรงกับคะแนนที่ใช้");
        record(points, PointTransaction.Type.RETURN, orderId, reason, at);
    }
    public List<PointTransaction> getPointHistoryMember() { return List.copyOf(memberPointHistory); }
    void initializeDemoPoints(int points, LocalDateTime at) {
        if (!memberPointHistory.isEmpty() || memberPointBalance != 0 || points < 0) throw new IllegalArgumentException("ตั้งคะแนนสาธิตได้ครั้งเดียว");
        record(points, PointTransaction.Type.INITIAL, null, "คะแนนตั้งต้นสำหรับสาธิต", at);
    }
    // Package helper retained for deterministic fixtures; every change has an audit entry.
    void changePoints(int delta) {
        record(delta, PointTransaction.Type.ADJUSTMENT, null, "ปรับยอดสำหรับชุดทดสอบ",
                LocalDateTime.now(ZoneId.of("Asia/Bangkok")));
    }
}
