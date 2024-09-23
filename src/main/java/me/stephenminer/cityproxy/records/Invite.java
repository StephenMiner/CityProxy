package me.stephenminer.cityproxy.records;

import java.util.UUID;

/**
 *
 * @param sender
 * @param invitee
 * @param roomName
 * @param permLevel 0 for friend, 1 for roommate
 */
public record Invite(UUID sender, UUID invitee, String roomName, int permLevel, String senderName) {


}
