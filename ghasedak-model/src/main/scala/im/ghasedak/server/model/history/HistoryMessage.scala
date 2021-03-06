package im.ghasedak.server.model.history

import java.time.LocalDateTime

import im.ghasedak.api.peer.ApiPeer

final case class HistoryMessage(
  userId:               Int,
  peer:                 ApiPeer,
  date:                 LocalDateTime,
  senderUserId:         Int,
  sequenceNr:           Int,
  messageContentHeader: Int,
  messageContentData:   Array[Byte],
  deletedAt:            Option[LocalDateTime]) {

  def ofUser(userId: Int): HistoryMessage = this.copy(userId = userId)

}

object HistoryMessage {

  def empty(userId: Int, peer: ApiPeer, date: LocalDateTime) =
    HistoryMessage(userId, peer, date, 0, 0, 0, Array.emptyByteArray, None)

}
