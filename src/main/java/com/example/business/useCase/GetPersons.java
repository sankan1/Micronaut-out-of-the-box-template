package com.example.business.useCase;

import com.example.business.adapter.PersonAdapter;
import com.example.jooq.person.tables.pojos.Person;
import io.getunleash.Unleash;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@Singleton
public class GetPersons {
    private static final Logger LOG = LoggerFactory.getLogger(GetPersons.class);

    private final PersonAdapter personAdapter;
    private final Unleash unleash;

    public GetPersons(PersonAdapter personAdapter, Unleash unleash) {
        this.personAdapter = personAdapter;
        this.unleash = unleash;
    }

    public List<Person> execute() {
        if (!unleash.isEnabled("get-persons")) {
            LOG.error("Fetch all persons is disabled!");
            return List.of();
        }
        try {
            List<Person> persons = personAdapter.getPersons();
            LOG.info("Successfully fetched {} persons!", persons.size());
            return persons;
        } catch (Exception e) {
            LOG.error("Failed to fetch persons", e);
            throw e;
        }
    }
}
