@RestController
@ApiMapping("/v1/workshops/")
public class InMemorySeatHoldController {

  private SeatHolds seatHolds;

  // use constructor injection; InMemorySeatHolds would be annotated with
  // @Service so Spring can inject it here
  InMemorySeatHoldController(SeatHolds seatHolds) {
    this.seatHolds = seatHolds;
  }

  // set min/max validations per real business requirements
  record AddWorkshopRequest(@Min(1) @Max(100) int capacity) {}

  record AddWorkshopResposne(String workshopId) {}

  // register workshop
  @PostMapping("/")
  ResponseEntity<AddWorkshopResponse> addWorkshop(
      @Valid AddWorkshopRequest request
  ) {
    var workshopId = new ULID().toString();
    seatHolds.addWorkshop(workshopId, request.capacity);
    return ResponseEntity.ok(new AddWorkshopResponse(workshopId));
  }

  record WorkshopDetailsResponse(int availableSeats) {}

  // get workshop details
  @GetMapping("/{workshopId}")
  ResponseEntity<WorkshopDetailsResponse> getWorkshopDetails(
    @Parameter("workshopId") @Valid @Regex(/*ulid validation goes here*/) workshopId,
  ) {
    return ResponseEntity.ok(new WorkshopDetailsResponse(seatHolds.available(workshopId)));
  }

  record AddReservationRequest(
      String customerId, // validate this per business customer id definition
      int seats, // set appropriate min/max
      Duration ttl, // set appropriate min/max
  ) {}

  record AddReservationResponse(String holdId) {}

  // create a reservation
  @PostMapping("/{workshopId}/reservations")
  ResponseEntity<AddResservationResponse> addReservation(
      @Parameter("workshopId") @Valid @Regex(/*ulid validation goes here*/) workshopId,
      @Valid AddReservationRequest request,
  ) {
    return ResponseEntity.ok(new AddReservationResponse(seatHolds.hold(workshopId, request.customerId, request.seats, request.ttl)));
  }

  // get reservation details
  // @GetMapping("/{workshopId}/reservations/{holdId}")
  // seatHolds doesn't expose an api for this so I'm skipping it for now

  record ConfirmReservationResponse() {}

  // confirm a reservation hold
  @PutMapping("/{workshopId}/reservations/{holdId}")
  ResponseEntity<ConfirmReservationResponse> confirmReservation(
      @Parameter("workshopId") String workshopId,
      @Parameter("holdId") String holdId
    ) {
    seatHolds.confirm(holdId);
    return ResponseEntity.ok(new ConfirmReservationResponse());
  }

  record ReleaseReservationResponse() {}

  // release a reservation hold
  @DeleteMapping("/{workhopId}/reservations/{holdId}")
  ResponseEntity<ReleaseReservationResponse> releaseReservation(
      @Parameter("workshopId") String workshopId,
      @Parameter("holdId") String holdId
  ) {
    seatHolds.release(holdId);
    return ResponseEntity.ok(new ReleaseReservationResponse());
  }
}

@RestControllerAdvice
class ExceptionHandler implements GlobalExceptionHandler {
  // Handle validation exception and seatHolds exceptions.
  // All exceptions should be mapped to a consistent format.
  // For validation exceptions, the status code would be 400
  // For seatHolds, exceptions it could be 404 or 500 depending on which scenario it is.
  // For seatHolds, I also think there's a status for Resource No longer valid which could be used for expired reservations.
}
