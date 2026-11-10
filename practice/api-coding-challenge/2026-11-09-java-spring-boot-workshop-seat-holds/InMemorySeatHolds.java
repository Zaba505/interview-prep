public class InMemorySeatHolds implements SeatHolds {

  private Clock clock;
  private int maxSeatsPerCustomer;
  private int holdCounter = 0;
  
  sealed interface ReservationStatus implemented by Confirmed, Unconfirmed {}

  record Confirmed() implements ReservationStatus {}

  record Unconfirmed(String holdId, Instant expiresAt) implements ReservationStatus {}

  record Reservation(String customerId, int seats, ReservationStatus status) {}

  record Workshop(List<Reservation> reservations, int capacity) {}

  private final HashMap<Workshop> workshops = new HashMap<>();

  public InMemorySeatHolds(Clock clock, int maxSeatsPerCustomer) {
   this.clock = clock;
   this.maxSeatsPerCustomer = maxSeatsPerCustomer;
  }

  void addWorkshop(String workshopId, int capacity) {
    this.workshops.put(workshopId, new Workshop(new HashMap<>(), capacity));
  }

  String hold(String workshopId, String customerId, int seats, Duration ttl) {
    var availableSeats = available(workshopId);
    if (availableSeats < seats) {
      throw new RuntimeException("this should be a custom exception for no more capacity")
    }

    // do other simple validations for negative/zero seats and ttl

    // probably negligible perf optimization so expiresAt is right before we exit
    var workshop = this.workshops.get(workshopId);
    holdCounter += 1;
    var holdId = "hold-"+holdCounter;
    var expiresAt = this.clock.now().add(ttl);
    var reservation = new Reservation(customerId, seats, Unconfirmed(holdId, expiresAt));

    workshop.reservations.add(reservation);
    
    return holdId;
  }

  int available(String workshopId) {
    var workshop = this.workshops.get(workshopId);
    return workshop.capacity - activeReservations(workshop.reservations).reduce(0, (acc, reservation) => acc + reservation.seats);
  }

  private List<Reservation> activeReservations(List<Reservation> reservations) {
    return workshops.get(workshopId).reservations.stream().filter(reservation -> {
      switch (reservation.status) {
        case Confirmed:
          return true;
        case Unconfirmed(holdId, expiresAt):
          return clock.now().isBefore(expiresAt);
      }
    }).collect(Collectors.toList());
  }

  void confirm(String holdId) {
    var workshop = getWorkshopByHoldId(holdId);

    // TODO: should probably map exception here to have better wording for
    // the possibility that it wasn't found because it expired
    var i = getReservationByHoldId(activeReservations(workshop.reservations), holdId);

    var reservation = workshop.reservation[i];
    workshop.reservations[i] = new Record(reservation.customerId, reservation.seats, new Confirmed())
  }

  private Workshop getWorkshopByHoldId(String holdId) {
    return this.workshops.values()
      .stream()
      .firstMatch(workshop -> workshop.reservations.anyMatch(reservation -> {
        switch (reservation.status) {
          case Confirmed:
            return false;
          case Unconfirmed(reservationHoldId, _):
            return reservationHoldId.equals(holdId);
        }
      }));
  }

  private int findReservationByHoldId(List<Reservation> reservations, String holdId) {
    for (var i = 0; i < reservations.size(); i++) {
      var reservation = reservations[i];
      switch (reservation.status) {
        case Confirmed:
          break; // this only breaks the switch and not the loop
        case Unconfirmed(reservationHoldId, _):
          if (!reservationHoldId.equals(holdId)) {
            break;
          }
          return i;
      }
    }

    throw new Exception("should be a custom exception for no hold found for id")
  }

  void release(String holdId) {
    var workshop = getWorkshopByHoldId(holdId);
    var i = getReservationByHold(activeReservations(workshop.reservations), holdId);
    workshop.reservations.pop(i);
  }
}
