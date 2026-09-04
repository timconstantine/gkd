package li.gkd.app.ui.component

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import li.gkd.app.MainActivity
import li.gkd.app.ui.share.LocalMainViewModel
import li.gkd.app.util.ShortUrlSet
import li.gkd.app.util.throttle


@Composable
fun TermsAcceptDialog() {
    val mainVm = LocalMainViewModel.current
    val context = LocalActivity.current as MainActivity
    val modifier = Modifier.fillMaxWidth()
    val stepDataList = remember {
        arrayOf(
            "Usage statement" to @Composable {
                val linkStyles = TextLinkStyles(
                    style = SpanStyle(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                )
                Text(
                    modifier = modifier,
                    text = buildAnnotatedString {
                        append("Thanks for using GKD! You need to read and agree to the ")
                        withLink(
                            LinkAnnotation.Url(
                                ShortUrlSet.URL12,
                                linkStyles
                            )
                        ) {
                            append("Terms of Use")
                        }
                        append(" and ")
                        withLink(
                            LinkAnnotation.Url(
                                ShortUrlSet.URL11,
                                linkStyles
                            )
                        ) {
                            append("Privacy Policy")
                        }
                        append(" to continue using the app. Please read them carefully")
                    },
                )
            },
            "About accessibility" to @Composable {
                Text(
                    modifier = modifier,
                    text = "GKD requests the system \"Accessibility API\" to read on-screen information, in order to perform automated actions based on your custom subscription rules",
                )
            }
        )
    }
    val step by mainVm.termsStepFlow.collectAsStateWithLifecycle()

    AppAlertDialog(
        onDismissRequest = {},
        title = {
            Text(text = stepDataList[step].first)
        },
        text = stepDataList[step].second,
        confirmButton = {
            TextButton(onClick = throttle {
                mainVm.acceptTermsStep(stepDataList.lastIndex)
            }) {
                Text(text = "Agree")
            }
        },
        dismissButton = {
            TextButton(onClick = throttle {
                context.finish()
            }) {
                Text(text = "Disagree")
            }
        }
    )
}
