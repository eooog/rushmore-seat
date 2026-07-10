package com.eooog.rushseat.domain.venue

object VenueFixture {
    fun venue(
        name: String = "Charm Dome",
        address: String? = null,
    ): Venue =
        Venue.create(
            name = name,
        )

    fun hall(
        venue: Venue,
        name: String,
        capacity: Int = 1000,
    ): Hall =
        Hall.create(
            venue = venue,
            name = name,
            capacity = capacity,
        )
}
