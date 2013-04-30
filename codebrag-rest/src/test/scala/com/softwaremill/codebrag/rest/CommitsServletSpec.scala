package com.softwaremill.codebrag.rest

import com.softwaremill.codebrag.service.user.Authenticator
import com.softwaremill.codebrag.AuthenticatableServletSpec
import org.scalatra.auth.Scentry
import com.softwaremill.codebrag.service.data.UserJson
import com.softwaremill.codebrag.dao.{CommitReviewTaskDAO, UserDAO, CommitInfoDAO}
import org.mockito.Mockito._
import com.softwaremill.codebrag.dao.reporting.{CommentListFinder, CommitListDTO, CommitListFinder, CommitListItemDTO}
import java.util.Date
import com.softwaremill.codebrag.service.diff.{DiffWithCommentsService, DiffService}
import com.softwaremill.codebrag.service.github.GitHubCommitImportServiceFactory
import com.softwaremill.codebrag.activities.AddCommentActivity
import org.bson.types.ObjectId
import com.softwaremill.codebrag.domain.CommitReviewTask


class CommitsServletSpec extends AuthenticatableServletSpec {

  val SamplePendingCommits = CommitListDTO(List(CommitListItemDTO("id", "abcd0123", "this is commit message", "mostr", "michal", new Date())))
  var commentActivity = mock[AddCommentActivity]
  var commitsInfoDao = mock[CommitInfoDAO]
  var commitsListFinder = mock[CommitListFinder]
  var diffService = mock[DiffWithCommentsService]
  var commentListFinder = mock[CommentListFinder]
  var userDao = mock[UserDAO]
  var commitReviewTaskDao = mock[CommitReviewTaskDAO]

  val importerFactory = mock[GitHubCommitImportServiceFactory]

  def bindServlet {
    addServlet(new TestableCommitsServlet(fakeAuthenticator, fakeScentry), "/*")
  }

  "GET /commits" should "respond with HTTP 401 when user is not authenticated" in {
    userIsNotAuthenticated
    get("/") {
      status should be(401)
    }
  }

  "GET /commits" should "should return commits pending review" in {
    val userId = new ObjectId
    val user = UserJson(userId.toString, "user", "user@email.com", "token")
    userIsAuthenticatedAs(user)
    when(commitsListFinder.findCommitsToReviewForUser(userId)).thenReturn(SamplePendingCommits)
    get("/") {
      status should be(200)
      body should equal(asJson(SamplePendingCommits))
    }
  }

  "DELETE /commits/:id" should "should remove commits from review list" in {
    val userId = new ObjectId
    val commitId = new ObjectId
    val user = UserJson(userId.toString, "user", "user@email.com", "token")
    userIsAuthenticatedAs(user)
    delete(s"/$commitId") {
      verify(commitReviewTaskDao).delete(CommitReviewTask(commitId, userId))
      status should be(200)
    }
  }

  class TestableCommitsServlet(fakeAuthenticator: Authenticator, fakeScentry: Scentry[UserJson])
    extends CommitsServlet(fakeAuthenticator, commitsListFinder, commentListFinder, commentActivity, commitReviewTaskDao, userDao, new CodebragSwagger, diffService, importerFactory) {
    override def scentry(implicit request: javax.servlet.http.HttpServletRequest) = fakeScentry
  }

}