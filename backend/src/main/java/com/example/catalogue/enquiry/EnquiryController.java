package com.example.catalogue.enquiry;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** HTTP layer for enquiries. @Valid rejects bad input with 400 before the service is called. */
@RestController
@RequestMapping("/api/enquiries")
public class EnquiryController {

    private final EnquiryService enquiryService;

    public EnquiryController(EnquiryService enquiryService) {
        this.enquiryService = enquiryService;
    }

    /** 200 is returned only after the mail server has accepted the email. */
    @PostMapping
    public EnquiryResponse submit(@Valid @RequestBody EnquiryRequest request) {
        return new EnquiryResponse(enquiryService.submit(request), "SENT");
    }
}
