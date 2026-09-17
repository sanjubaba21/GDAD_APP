# GDAD BAGS Microsoft Store listing draft

This file is the reviewed source copy for the first Windows Store submission. Keep the public
listing consistent with the behavior verified in `PROJECT_STATUS.md`; do not promise functionality
that has not passed the release gate.

## Submission target

- Product: `Gdad Bags`
- Store ID: `9P7KV7QPB1LW`
- Package identity: `GDadbags.GdadBags`
- Supported device family: Windows 10/11 Desktop only
- Price: Free
- Audience: Public
- Release schedule: As soon as possible, only after certification is explicitly approved

## English listing

### Description

GDAD BAGS helps a bag shop manage daily sales, purchases, inventory, vendors, cash and bank
accounts, and business reports from one secure workspace.

Owners can record negotiated selling prices for each sale, post purchase bills manually or import
the supported Excel purchase template, track FIFO stock cost and vendor dues, and review trusted
period reports. Sales staff receive role-appropriate access for day-to-day selling without owner
administration controls.

The Windows application uses the same protected Supabase business data and accounting rules as the
GDAD BAGS Android application. Financial postings require an internet connection; cached read-only
information may remain available during a temporary connection failure.

### Product features

1. Flexible per-sale pricing for bargaining workflows
2. FIFO inventory cost and cost-based gross-profit reporting
3. Product, stock, vendor, purchase, sale, and return management
4. Cash and bank ledgers with deposits, withdrawals, expenses, transfers, and reversals
5. Excel purchase-bill import with automatic product creation and a review step
6. Automatic receipt details for manually posted purchases
7. Owner and Salesman role-based access
8. Nepal-focused dates, currency display, and business reporting
9. Secure hosted authentication and tenant-scoped shop data
10. Safe offline cached reads with online-only financial posting

### Short description

Sales, inventory, purchases, vendors, cash and bank, and trusted reports for GDAD BAGS.

### Keywords

`sales`, `inventory`, `purchase`, `vendor`, `accounting`, `bags`, `Nepal`

### Copyright and developer

- Copyright and trademark info: `© 2026 G Dad bags. All rights reserved.`
- Developed by: `G Dad bags`

## Nepali listing source

Use this copy for both `Nepali` and `Nepali (Nepal)` only if those additional listing languages are
kept in the submission.

### Description

GDAD BAGS ले झोला पसलको दैनिक बिक्री, खरिद, मौज्दात, आपूर्तिकर्ता, नगद तथा बैंक खाता र व्यापारिक
प्रतिवेदन एउटै सुरक्षित प्रणालीबाट व्यवस्थापन गर्न मद्दत गर्छ।

पसलधनीले प्रत्येक बिक्रीमा मोलमोलाइअनुसार फरक मूल्य राख्न, खरिद बिल हातैले पोस्ट गर्न वा समर्थित
Excel खरिद टेम्प्लेट आयात गर्न, FIFO लागतअनुसार मौज्दात र आपूर्तिकर्ताको बाँकी रकम हेर्न तथा अवधिगत
प्रतिवेदन समीक्षा गर्न सक्छन्। बिक्री कर्मचारीले प्रशासनिक अधिकारबिना आफ्नो भूमिकाअनुसार दैनिक बिक्री
काम गर्न सक्छन्।

Windows अनुप्रयोगले GDAD BAGS Android अनुप्रयोगकै सुरक्षित Supabase डाटा र लेखा नियम प्रयोग गर्छ।
आर्थिक कारोबार पोस्ट गर्न इन्टरनेट आवश्यक हुन्छ; अस्थायी रूपमा जडान नहुँदा पहिले सुरक्षित गरिएको
पढ्न-मात्र मिल्ने जानकारी उपलब्ध रहन सक्छ।

### Product features

1. मोलमोलाइअनुसार प्रत्येक बिक्रीमा फरक मूल्य
2. FIFO मौज्दात लागत र लागतमा आधारित कुल नाफा प्रतिवेदन
3. सामान, मौज्दात, आपूर्तिकर्ता, खरिद, बिक्री र फिर्ता व्यवस्थापन
4. नगद तथा बैंक जम्मा, झिकाइ, खर्च, स्थानान्तरण र उल्ट्याउने सुविधा
5. Excel खरिद बिल आयात, स्वतः सामान सिर्जना र पोस्ट गर्नुअघि समीक्षा
6. हातैले पोस्ट गरिएको खरिदको स्वतः बिल विवरण
7. पसलधनी र बिक्री कर्मचारीका छुट्टाछुट्टै अधिकार
8. नेपालअनुकूल मिति, मुद्रा र व्यापारिक प्रतिवेदन
9. सुरक्षित लगइन र पसलअनुसार छुट्टिएको क्लाउड डाटा
10. अफलाइनमा सुरक्षित पढाइ; आर्थिक कारोबारका लागि अनलाइन आवश्यक

### Short description

GDAD BAGS का लागि बिक्री, मौज्दात, खरिद, आपूर्तिकर्ता, नगद, बैंक र विश्वसनीय प्रतिवेदन।

## Required assets before saving a listing

- At least one genuine desktop-app PNG screenshot is mandatory.
- Use 1366 x 768 pixels or larger; 1920 x 1080 is preferred.
- Capture real application UI with no PIN, account ID, token, customer/vendor personal data, or
  production financial values visible. Seeded or clearly non-sensitive demonstration data is safest.
- Recommended first set: login screen, Owner dashboard, flexible-price sale entry, Excel purchase
  preview, and period report.
- Store logos are optional for Desktop because the package supplies its GDAD icon. Do not upload
  Xbox artwork and do not enable Xbox as a device family.

## Submission guardrails

- Upload only the MSIX extracted from protected GitHub artifact `10450346704` after its checksum,
  manifest identity, architecture, version, and file inventory are independently verified.
- Keep the application free. Do not enable paid acquisition, trials, subscriptions, or paid services.
- Do not submit for certification until package validation, listing review, privacy/support fields,
  and the complete draft have been checked and the user confirms the final submission action.
