package com.omnichannel.reservation.inventory.domain.exception;

import com.omnichannel.reservation.shared.domain.exception.BusinessException;

public class HaveMerchandise extends BusinessException {
    public HaveMerchandise() {
        super("Vé VIP đã bao gồm Merchandise Kit độc quyền, không được chọn mua thêm vật phẩm lẻ");
    }

    public HaveMerchandise(String message) {
        super(message);
    }
}
