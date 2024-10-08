package com.example.finalexam.utils;

import com.example.finalexam.constants.DateFormatConstants;
import com.example.finalexam.constants.FilePaths;
import com.example.finalexam.model.Player;
import com.example.finalexam.model.Team;
import com.example.finalexam.repository.PlayerRepository;
import com.example.finalexam.repository.TeamRepository;
import com.example.finalexam.constants.ErrorMessages;
import com.example.finalexam.model.Match;
import com.example.finalexam.model.MatchRecord;
import com.example.finalexam.repository.MatchRepository;
import com.example.finalexam.repository.MatchRecordRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class CsvParser {

    private static final Logger logger = LoggerFactory.getLogger(CsvParser.class);

    private final PlayerRepository playerRepository;
    private final TeamRepository teamRepository;
    private final MatchRepository matchRepository;
    private final MatchRecordRepository recordRepository;
    private final FilePaths filePaths;
    private final ResourceLoader resourceLoader;

    @Autowired
    public CsvParser(PlayerRepository playerRepository, TeamRepository teamRepository, MatchRepository matchRepository, MatchRecordRepository recordRepository, FilePaths filePaths, ResourceLoader resourceLoader) {
        this.playerRepository = playerRepository;
        this.teamRepository = teamRepository;
        this.matchRepository = matchRepository;
        this.recordRepository = recordRepository;
        this.filePaths = filePaths;
        this.resourceLoader = resourceLoader;
    }

    //Populating the database when starting the application
    @PostConstruct
    public void populateDatabase() {
        try {
            //Load teams first
            loadTeams();
            logger.info(ErrorMessages.TEAMS_LOADING_MESSAGE);
            //Load players after teams
            loadPlayers();
            logger.info(ErrorMessages.PLAYERS_LOADING_MESSAGE);
            //Load matches after players
            loadMatches();
            logger.info(ErrorMessages.MATCHES_LOADING_MESSAGE);
            //Load records last
            loadRecords();
            logger.info(ErrorMessages.RECORDS_LOADING_MESSAGE);
        } catch (IOException e) {
            logger.error(ErrorMessages.LOADING_ERROR_MESSAGE);
        }
    }

    private List<String[]> readCsv(String filePath) throws IOException {
        List<String[]> rows = new ArrayList<>();
        Resource resource = resourceLoader.getResource(filePath);
        try (BufferedReader br = new BufferedReader(new InputStreamReader(resource.getInputStream()))) {
            String line;
            boolean isFirstRow = true;
            while ((line = br.readLine()) != null) {
                if (isFirstRow) {
                    isFirstRow = false; //Skip header
                    continue;
                }
                String[] values = line.split(",");
                rows.add(values); // Add the values array to the list
            }
        }
        return rows;
    }

    private void loadPlayers() throws IOException {
        List<String[]> rows = readCsv(filePaths.getPlayersFilePath());
        List<Player> players = new ArrayList<>();

        List<Team> teams = teamRepository.findAll();
        Map<Long, Team> teamIdMapping = teams.stream()
                .collect(Collectors.toMap(Team::getId, Function.identity()));

        for (String[] values : rows) {

            //Fetch the corresponding team based on the TeamID from the CSV
            Long teamId = Long.parseLong(values[4].trim());
            Team team = teamIdMapping.get(teamId);

            // Create a new Player object and set its fields
            Player player = new Player();
            player.setId(Long.parseLong(values[0].trim()));
            player.setTeamNumber(Integer.parseInt(values[1].trim()));
            player.setPosition(values[2].trim());
            player.setFullName(values[3].trim());
            player.setTeam(team); //Assign the team to the player

            // Add the player to the list
            players.add(player);
        }

        // Save all players to the database
        playerRepository.saveAll(players);
    }

    private void loadMatches() throws IOException {
        List<String[]> rows = readCsv(filePaths.getMatchesFilePath());
        List<Match> matches = new ArrayList<>();

        for (String[] values : rows) {
            //Create a new Match object and set its fields
            Match match = new Match();
            match.setId(Long.parseLong(values[0].trim()));
            match.setaTeamID(Long.parseLong(values[1].trim()));
            match.setbTeamID(Long.parseLong(values[2].trim()));
            //Parse the date from the CSV
            LocalDate matchDate = parseDate(values[3].trim());
            match.setDate(matchDate);
            match.setScore(values[4].trim());

            //Add the match to the list
            matches.add(match);
        }

        //Save all teams to the database
        matchRepository.saveAll(matches);
    }

    private void loadTeams() throws IOException {
        List<String[]> rows = readCsv(filePaths.getTeamsFilePath());
        List<Team> teams = new ArrayList<>();

        for (String[] values : rows) {
            //Create a new Team object and set its fields
            Team team = new Team();
            team.setId(Long.parseLong(values[0].trim()));
            team.setName(values[1].trim());
            team.setManagerFullName(values[2].trim());
            team.setTeamGroup(values[3].trim());

            teams.add(team);
        }
        //Save all teams to the database
        teamRepository.saveAll(teams);
    }

    private void loadRecords() throws IOException {
        List<String[]> rows = readCsv(filePaths.getRecordsFilePath());
        List<MatchRecord> records = new ArrayList<>();

        List<Player> players = playerRepository.findAll();
        Map<Long, Player> playerIdMapping = players.stream()
                .collect(Collectors.toMap(Player::getId, Function.identity()));

        List<Match> matches = matchRepository.findAll();
        Map<Long, Match> matchIdMapping = matches.stream()
                .collect(Collectors.toMap(Match::getId, Function.identity()));

        for (String[] values : rows) {

            //Fetch the corresponding player and match based on the player id and match id
            Long playerId = Long.parseLong(values[1].trim());
            Long matchId = Long.parseLong(values[2].trim());

            Player player = playerIdMapping.get(playerId);
            Match match = matchIdMapping.get(matchId);

            //Handle if the player ot match doesn't exist
            if (player == null || match == null) {
                continue; //Skip if player or match is not found
            }
            //Create a new Record object and set its fields
            MatchRecord matchRecord = new MatchRecord();
            matchRecord.setId(Long.parseLong(values[0].trim()));
            matchRecord.setPlayer(player);
            matchRecord.setMatch(match);
            matchRecord.setFromMinutes(Integer.parseInt(values[3].trim()));
            //Handle null in toMinutes (assuming 90 minutes if NULL)
            if (values[4].trim().equalsIgnoreCase("NULL")) {
                matchRecord.setToMinutes(90);
            } else {
                matchRecord.setToMinutes(Integer.parseInt(values[4].trim()));
            }
            //Add the record to the list
            records.add(matchRecord);
        }
        //Save all record to the database
        recordRepository.saveAll(records);
    }

    private LocalDate parseDate(String date) {
        for (DateTimeFormatter formatter : DateFormatConstants.DATE_FORMATTERS) {
            try {
                return LocalDate.parse(date, formatter);
            } catch (DateTimeParseException e) {
                //Ignore
            }
        }
        throw new IllegalArgumentException(String.format(ErrorMessages.DATE_FORMAT_MESSAGE, date));
    }

}