@RestController
@ApiMapping("/v1")
class DuplicateSupplierController {
  private final PostgresClient postgresClient;

  DuplicateSuppliercontroller(PostgresClient postgresClient) {
    this.postgresClient = postgresClient;
  }

  // upload suppliers
  @PostMapping("/suppliers")
  ResponseEntity<UploadSuppliersResponse> uploadSuppliers(
      @RequestBody UploadSuppliersRequest request
  ) {

    var uploadId = new ULID().toString();

    var suppliers = request.suppliers()
      .stream()
      .map(supplier -> {
        var status = SupplierStatus.Rejected;
        if (supplier.email() != null && supplier.website() != null) {
          status = SupplierStatus.Accepted;
        }
        return new SupplierRow(
            new ULID().toString(),
            uploadId,
            supplier.name(),
            supplier.email(),
            supplier.website(),
            status
        );
      })
      .collect(Collectors.toList());
    
    // TODO: handle collisions with ON CONFLICT
    postgresClient.execute(
      """
      BEGIN
      INSERT INTO suppliers (supplier_id, upload_id, name, website, email, status)
      VALUES (%i)
      COMMIT
      """,
      suppliers
    );

    return ResponseEntity.created(new UploadSuppliersResponse(uploadId)).header("Location", "/v1/suppliers/uploads/"+uploadId);
  }

  // get report of uploaded suppliers
  @GetMapping("/suppliers/uploads/{uploadId}")
  ResponseEntity<UploadDetailsResponse> getUploadDetails(
      @PathVariable uploadId
  ) {
    var results = postgresClient.execute(
      """
      BEGIN
      SELECT supplier_id, upload_id, name, website, email, status
      FROM suppliers
      WHERE upload_id = %i
      COMMIT
      """,
      uploadId
    );

    int acceptedSuppliers = 0;
    List<SupplierWithId> rejectedSuppliers = new ArrayList<>();

    for (var result: results) {
      var row = (SupplierRow) result;
      if (row.status() == SupplierStatus.Accepted) {
        acceptedSuppliers += 1;
        continue;
      }

      rejectedSuppliers.add(new SupplierWithId(
            row.supplierId(),
            row.name(),
            row.website(),
            row.email()
      ));
    }

    return ResponseEntity.ok(new UploadDetailsResponse(acceptedSuppliers, rejectedSuppliers));
  }

  // get all suppliers grouped
  // due to time constraint I won't be adding pagination but that would be required
  // depending on the number of suppliers and whether the backing database is ever purged
  @GetMapping("/suppliers")
  ResponseEntity<SuppliersResponse> getDuplicateSuppliers() {

  }
}

enum SupplierStatus {
  Accepted,
  Rejected;
}

record SupplierRow(
    String supplierId,
    String uploadId,
    String name,
    String website,
    String email,
    SupplierStatus status
) {}

record Supplier(String name, String website, String email) {}

record UploadSuppliersRequest(
    // would add annotations to the Supplier record
    // to validate email and website formats at minimum but because of
    // the rejection requirement we need to accept the entire payload,
    // validate internally and store validation results for future lookup
    @Valid List<Supplier> suppliers) {}

record UploadSuppliersResponse(String uploadId) {}

record SupplierWithId(String id, String name, String website, String email) {}

record UploadDetailsResponse(
  int numberOfAcceptedSuppliers,
  List<SupplierWithId> rejectSuppliers
) {}

record SuppliersResponse(
  List<SupplierGroup> suppliers
) {}

record SupplierGroup(
    String normalizedEmail,
    String normalizedWebsite,
    Map<String, String> supplierIds // map supplier ids to names
) {}
