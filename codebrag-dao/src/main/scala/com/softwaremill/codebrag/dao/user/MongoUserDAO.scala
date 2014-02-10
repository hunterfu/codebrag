package com.softwaremill.codebrag.dao.user

import com.softwaremill.codebrag.domain._
import net.liftweb.mongodb.record.{BsonMetaRecord, BsonRecord, MongoMetaRecord, MongoRecord}
import net.liftweb.mongodb.record.field.{BsonRecordField, ObjectIdPk}
import com.foursquare.rogue.LiftRogue._
import org.bson.types.ObjectId
import org.joda.time.DateTime
import net.liftweb.record.field.{BooleanField, OptionalDateTimeField}
import com.softwaremill.codebrag.dao.LongStringField

class MongoUserDAO extends UserDAO {

  import UserImplicits._

  override def add(user: User) = {
    val toSave = if (user.id == null) {
      user.copy(id = new ObjectId())
    } else user
    toSave.save(safe = true)
  }

  override def findAll() = {
    UserRecord.asRegularUser.fetch()
  }

  override def findByEmail(email: String) = {
    UserRecord.asRegularUser.where(_.email eqs email.toLowerCase).get()
  }

  override def findByLowerCasedLogin(login: String) = {
    UserRecord.asRegularUser.where(_.authentication.subfield(_.usernameLowerCase) eqs login.toLowerCase).get()
  }

  def findByLoginOrEmail(login: String, email: String) = {
    val lowercasedLogin = login.toLowerCase
    val lowercasedEmail = email.toLowerCase
    UserRecord.asRegularUser.or(_.where(_.authentication.subfield(_.usernameLowerCase) eqs lowercasedLogin), _.where(_.email eqs lowercasedEmail)) get()
  }

  def findByToken(token: String) = {
    UserRecord.asRegularUser.where (_.token eqs token) get()
  }

  def findById(userId: ObjectId) = {
    UserRecord.asRegularUser.where(_.id eqs userId).get()
  }

  def changeAuthentication(id: ObjectId, authentication: Authentication) {
    UserRecord.asRegularUser.where(_.id eqs id).modify(_.authentication setTo (authentication)).updateOne()
  }

  def rememberNotifications(id: ObjectId, notifications: LastUserNotificationDispatch) {
    UserRecord.asRegularUser.where(_.id eqs id).modify(_.notifications setTo notifications).updateOne()
  }

  def findCommitAuthor(commit: CommitInfo) = {
    UserRecord.asRegularUser.or(_.where(_.name eqs commit.authorName), _.where(_.email eqs commit.authorEmail)).get()
  }

  def changeUserSettings(userId: ObjectId, newSettings: UserSettings) {
    UserRecord.asRegularUser.where(_.id eqs userId).modify(_.userSettings setTo toRecord(newSettings)).updateOne()
  }

  private object UserImplicits {
    implicit def fromRecord(user: UserRecord): User = {
      new User(user.id.get, user.authentication.get, user.name.get, user.email.get, user.token.get, user.userSettings.get, user.notifications.get)
    }

    implicit def fromRecords(users: List[UserRecord]): List[User] = {
      users.map(fromRecord)
    }

    implicit def fromOptionalRecord(userOpt: Option[UserRecord]): Option[User] = {
      userOpt.map(fromRecord)
    }

    implicit def toRecord(user: User): UserRecord = {
      UserRecord.createRecord.id(user.id)
        .name(user.name)
        .token(user.token)
        .email(user.email)
        .authentication(user.authentication)
        .userSettings(user.settings)
        .notifications(user.notifications)
    }

    implicit def toRecord(authentication: Authentication): AuthenticationRecord = {
      AuthenticationRecord.createRecord
        .provider(authentication.provider)
        .username(authentication.username)
        .usernameLowerCase(authentication.usernameLowerCase)
        .token(authentication.token)
        .salt(authentication.salt)
    }

    implicit def fromRecord(record: AuthenticationRecord): Authentication = {
      Authentication(record.provider.get, record.username.get, record.usernameLowerCase.get, record.token.get, record.salt.get)
    }

    implicit def toRecord(notifications: LastUserNotificationDispatch): LastUserNotificationDispatchRecord = {
      val record = LastUserNotificationDispatchRecord.createRecord
      notifications match {
        case LastUserNotificationDispatch(None, None) => record
        case LastUserNotificationDispatch(Some(commits), None) => record.commits(commits.toGregorianCalendar)
        case LastUserNotificationDispatch(Some(commits), Some(followups)) => record.commits(commits.toGregorianCalendar).followups(followups.toGregorianCalendar)
        case LastUserNotificationDispatch(None, Some(followups)) => record.followups(followups.toGregorianCalendar)
      }
    }

    implicit def fromRecord(record: LastUserNotificationDispatchRecord): LastUserNotificationDispatch = {
      val commitsDate = record.commits.get
      val followupsDate = record.followups.get
      LastUserNotificationDispatch(commitsDate.map(new DateTime(_)), followupsDate.map(new DateTime(_)))
    }

    implicit def fromRecord(recordOpt: Option[LastUserNotificationDispatchRecord]): Option[LastUserNotificationDispatch] = {
      recordOpt.map(fromRecord)
    }

    implicit def fromRecord(record: UserSettingsRecord): UserSettings = {
      UserSettings(record.avatarUrl.get, record.emailNotificationsEnabled.get, record.dailyUpdatesEmailEnabled.get, record.appTourDone.get)
    }

    implicit def toRecord(settings: UserSettings): UserSettingsRecord = {
      UserSettingsRecord.createRecord
        .avatarUrl(settings.avatarUrl)
        .emailNotificationsEnabled(settings.emailNotificationsEnabled)
        .dailyUpdatesEmailEnabled(settings.dailyUpdatesEmailEnabled)
        .appTourDone(settings.appTourDone)
    }
  }

}

class UserRecord extends MongoRecord[UserRecord] with ObjectIdPk[UserRecord] {
  def meta = UserRecord

  object authentication extends BsonRecordField(this, AuthenticationRecord)

  object name extends LongStringField(this)

  object email extends LongStringField(this)

  object token extends LongStringField(this)

  object userSettings extends BsonRecordField(this, UserSettingsRecord)

  object notifications extends BsonRecordField(this, LastUserNotificationDispatchRecord)

  object regular extends BooleanField(this, true)

}

object UserRecord extends UserRecord with MongoMetaRecord[UserRecord] {
  override def collectionName = "users"

  val asRegularUser = UserRecord where(_.regular eqs true)
}

class AuthenticationRecord extends BsonRecord[AuthenticationRecord] {
  def meta = AuthenticationRecord

  object provider extends LongStringField(this)

  object username extends LongStringField(this)

  object usernameLowerCase extends LongStringField(this)

  object token extends LongStringField(this)

  object salt extends LongStringField(this)

}

object AuthenticationRecord extends AuthenticationRecord with BsonMetaRecord[AuthenticationRecord]

class LastUserNotificationDispatchRecord extends BsonRecord[LastUserNotificationDispatchRecord] {
  def meta = LastUserNotificationDispatchRecord

  object commits extends OptionalDateTimeField(this)

  object followups extends OptionalDateTimeField(this)

}

object LastUserNotificationDispatchRecord extends LastUserNotificationDispatchRecord with BsonMetaRecord[LastUserNotificationDispatchRecord]

class UserSettingsRecord extends BsonRecord[UserSettingsRecord] {
  def meta = UserSettingsRecord

  object avatarUrl extends LongStringField(this)

  object emailNotificationsEnabled extends BooleanField(this)

  object dailyUpdatesEmailEnabled extends BooleanField(this)

  object appTourDone extends BooleanField(this)

}

object UserSettingsRecord extends UserSettingsRecord with BsonMetaRecord[UserSettingsRecord]

