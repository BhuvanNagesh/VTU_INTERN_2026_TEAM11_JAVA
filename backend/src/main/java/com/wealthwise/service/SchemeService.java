package com.wealthwise.service;

import com.wealthwise.model.Scheme;
import com.wealthwise.repository.SchemeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Logger;

@Service
public class SchemeService {

    private static final Logger log = Logger.getLogger(SchemeService.class.getName());
    private static final String AMFI_URL = "https://www.amfiindia.com/spages/NAVAll.txt";
    private static final DateTimeFormatter NAV_DATE_FMT = DateTimeFormatter.ofPattern("dd-MMM-yyyy");

    @Autowired
    private SchemeRepository schemeRepository;

    // ─── Search ──────────────────────────────────────────────────────────────

    public Page<Scheme> search(String query, String category, String planType, int page, int size) {
        PageRequest pr = PageRequest.of(page, size, Sort.by("schemeName").ascending());
        if (query == null || query.isBlank()) {
            return schemeRepository.findAll(pr);
        }
        if ((category == null || category.isBlank()) && (planType == null || planType.isBlank())) {
            return schemeRepository.searchActive(query.trim(), pr);
        }
        return schemeRepository.searchFiltered(
            query.trim(),
            (category == null || category.isBlank()) ? null : category.toUpperCase(),
            (planType == null || planType.isBlank()) ? null : planType.toUpperCase(),
            pr
        );
    }

    public Optional<Scheme> findByAmfiCode(String amfiCode) {
        return schemeRepository.findByAmfiCode(amfiCode);
    }

    public long countActive() {
        return schemeRepository.countByIsActiveTrue();
    }


}
